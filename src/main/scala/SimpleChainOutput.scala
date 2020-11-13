package rspTesting

import dspblocks._

import dsptools._
import dsptools.numbers._

import chisel3._
import chisel3.experimental._
import chisel3.util._

import fft._
import magnitude._

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem.{BaseSubsystem, CrossingWrapper}
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf
import freechips.rocketchip.util._


case class SimpleChainOutputParameters (
  fftParams        : FFTParams[FixedPoint],
  logMagMuxParams  : MAGParams[FixedPoint],
  logMagMuxAddress : AddressSet,
  fftAddress       : AddressSet,
  fftRAM           : AddressSet,
  beatBytes        : Int
)

class SimpleChainOutput(val params: SimpleChainOutputParameters)(implicit p: Parameters) extends LazyModule {
  
  val fft = LazyModule(new FFTBlockWithWindowing(csrAddress = params.fftAddress, ramAddress = params.fftRAM, params.fftParams, beatBytes = params.beatBytes))
  val in_adapt = AXI4StreamWidthAdapter.oneToN(params.beatBytes)
  val in_queue = LazyModule(new AXI4StreamBuffer(BufferParams(1, true, true), beatBytes = 4))
  val mux = LazyModule(new AXI4LogMagMuxBlock(params.logMagMuxParams, params.logMagMuxAddress, params.beatBytes))
  
  val lhs = AXI4StreamIdentityNode()
  val rhs = AXI4StreamIdentityNode()
  
  rhs := in_adapt := in_queue.node := mux.streamNode := fft.streamNode := lhs
  
  // From standalone blocks
  val streamNode = NodeHandle(lhs.inward, rhs.outward)
  
  lazy val blocks = Seq(fft, mux)
  val bus = LazyModule(new AXI4Xbar)
  val mem = Some(bus.node)
  for (b <- blocks) {
    b.mem.foreach { _ := bus.node }
  }
  
  lazy val module = new LazyModuleImp(this)
}

object SimpleChainOutputApp extends App
{
    val params = SimpleChainOutputParameters (
    fftParams = FFTParams.fixed(
      dataWidth = 16,
      twiddleWidth = 16,
      numPoints = 16,
      useBitReverse  = true,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(16))(0),
      keepMSBorLSB = Array.fill(log2Up(16))(true),
    ),
    fftAddress      = AddressSet(0x60000100, 0xFF),
    fftRAM          = AddressSet(0x60002000, 0xFFF),
    logMagMuxParams = MAGParams.fixed(
      dataWidth       = 16,
      binPoint        = 0,
      dataWidthLog    = 16,
      binPointLog     = 9,
      log2LookUpWidth = 9,
      useLast         = true,
      numAddPipes     = 1,
      numMulPipes     = 1
    ),
    logMagMuxAddress  = AddressSet(0x60000080, 0xF),
    beatBytes         = 4)

  implicit val p: Parameters = Parameters.empty
  val standaloneModule = LazyModule(new SimpleChainOutput(params) {

    def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
    val ioMem = mem.map { m => {
      val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))

      m :=
        BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
        ioMemNode

      val ioMem = InModuleBody { ioMemNode.makeIO() }
      ioMem
    }}
    
    val ioInNode = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 4)))
    val ioOutNode = BundleBridgeSink[AXI4StreamBundle]()
    
    // ioOutNode
    ioOutNode :=
      AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) :=
      streamNode :=
      BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 4)) :=
      ioInNode

    val in = InModuleBody { ioInNode.makeIO() }
    val out = InModuleBody { ioOutNode.makeIO() }
  })
  chisel3.Driver.execute(Array("--target-dir", "verilog/SimpleChainOutput", "--top-name", "SimpleChainOutput"), ()=> standaloneModule.module) // generate verilog code
}
