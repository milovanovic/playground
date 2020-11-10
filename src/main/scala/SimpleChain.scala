package simpleChain

import dspblocks._

import dsptools._
import dsptools.numbers._

import chisel3._
import chisel3.experimental._
import chisel3.util._
//import nordic.accelerator.fft._
import fft._

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


case class SimpleChainParameters (
  fftParams       : FFTParams[FixedPoint],
  fftAddress      : AddressSet,
  fftRAM          : AddressSet,
  beatBytes       : Int
)

// changed a little bit comparing to standard AXI4StreamBuffer
class AXI4StreamBuffer(params: BufferParams, beatBytes: Int) extends LazyModule()(Parameters.empty) {
val innode = AXI4StreamSlaveNode(AXI4StreamSlaveParameters())
val outnode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters( "buffer", n = beatBytes)))))
val node = NodeHandle(innode, outnode)

lazy val module = new LazyModuleImp(this) {
  val (in, _) = innode.in(0)
  val (out, _) = outnode.out(0)

  val queue = Queue.irrevocable(in, params.depth, pipe=params.pipe, flow=params.flow)
  out.valid := queue.valid
  out.bits := queue.bits
  queue.ready := out.ready
}
}

class SimpleChain(val params: SimpleChainParameters)(implicit p: Parameters) extends LazyModule {
  
  val fft = LazyModule(new FFTBlockWithWindowing(csrAddress = params.fftAddress, ramAddress = params.fftRAM, params.fftParams, beatBytes = params.beatBytes))
  val in_adapt = AXI4StreamWidthAdapter.nToOne(params.beatBytes)
  val in_queue = LazyModule(new AXI4StreamBuffer(BufferParams(1, true, true), beatBytes = 1))
  
  val lhs = AXI4StreamIdentityNode()
  val rhs = AXI4StreamIdentityNode()
  
  rhs := fft.streamNode := in_adapt := in_queue.node := lhs
  // From standalone blocks
  val streamNode = NodeHandle(lhs.inward, rhs.outward)

  val mem = fft.mem
  
  lazy val module = new LazyModuleImp(this)
}

object SimpleChainApp extends App
{
    val params = SimpleChainParameters (
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
    beatBytes      = 4)

  implicit val p: Parameters = Parameters.empty
  val standaloneModule = LazyModule(new SimpleChain(params) {

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

    ioOutNode :=
      AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) :=
      streamNode :=
      BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 1)) :=
      ioInNode

    val in = InModuleBody { ioInNode.makeIO() }
    val out = InModuleBody { ioOutNode.makeIO() }
  })
  chisel3.Driver.execute(Array("--target-dir", "verilog/simpleChain", "--top-name", "simpleChain"), ()=> standaloneModule.module) // generate verilog code
}
















