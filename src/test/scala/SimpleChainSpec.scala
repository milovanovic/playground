package rspTesting

import chisel3.DontCare
import freechips.rocketchip.interrupts._

import dsptools._
import dsptools.numbers._

import chisel3.experimental._
import chisel3._
import chisel3.util._
import chisel3.iotesters.Driver

import chisel3.iotesters.PeekPokeTester
import dspblocks.{AXI4DspBlock, AXI4StandaloneBlock, TLDspBlock, TLStandaloneBlock}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{PeripheryBus, PeripheryBusParams}
import freechips.rocketchip.system.BaseConfig
import freechips.rocketchip.tilelink.{TLBundleParameters, TLFragmenter, TLIdentityNode, TLToAXI4}

import org.scalatest.{FlatSpec, Matchers}
import breeze.math.Complex
import breeze.signal.{fourierTr, iFourierTr}
import breeze.linalg._
import breeze.plot._

import fft._

trait SimpleChainInputPins extends SimpleChainInput {
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
}

class SimpleChainInputBlockTester
(
  dut: SimpleChainInput with SimpleChainInputPins,
  params: SimpleChainInputParameters,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.in)
  val numPoints = params.fftParams.numPoints
  
  // expected peak at bin 16 and at bin 512-16
  val inData = RspTesterUtils.getTone(numSamples = params.fftParams.numPoints, 0.03125)//f1r = 0.3567/8)
  //val inData = RspTesterUtils.genRandSignal(numSamples = numPoints)
  
  println("Input data is:")
  inData.map(c => println(c.toString))
  //inData.map(c => println(RspTesterUtils.asNdigitBinary(c, 16)))
  
  val scalaFFT = fourierTr(DenseVector(inData.toArray)).toScalaVector
  val scalaForPlot = scalaFFT.map(c => c.abs.toLong).toSeq

  RspTesterUtils.plot_data(inData)
  RspTesterUtils.plot_fft(scalaForPlot)
  
  println("Expected result should be: ")
  scalaFFT.map(c => println((c/numPoints).toString))
  
  var dataByte = Seq[Int]()
  
  // split 32 bit data to 4 bytes
  // send real sinusoid
  for (i <- inData) {
    // imag part
    dataByte = dataByte :+ 0
    dataByte = dataByte :+ 0
    // real part
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
  }
  
  //println("Input data to queue:")
  //dataByte.map(c => println(c.toString))
  //dataByte.map(c => println(RspTesterUtils.asNdigitBinary(c, 16)))
  
  poke(dut.out.ready, true.B) // make output always ready to accept data

  step(1)
   // add master transactions
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))

  var outRealSeq = Seq[Int]()
  var outImagSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  var tmpReal: Double = 0.0
  var tmpImag: Double = 0.0
  
  // this logic perhaps can be simplified
  while (outRealSeq.length < numPoints) {
    if (peek(dut.out.valid) == 1) {
      peekedVal = peek(dut.out.bits.data)
      tmpReal = ((peekedVal.toInt/math.pow(2,16)).toShort).toDouble
      tmpImag = ((peekedVal.toInt - (tmpReal.toInt * math.pow(2,16))).toShort).toDouble
      outRealSeq = outRealSeq :+ tmpReal.toInt
      outImagSeq = outImagSeq :+ tmpImag.toInt
    }
    step(1)
  }
  
  val complexOut = outRealSeq.zip(outImagSeq).map { case (real, imag) => Complex(real, imag) }
  
  // useful for small number of points, for fast check
  println("Received data should be: ")
  complexOut.map(c => println(c.toString))
  
  val chiselFFTForPlot = complexOut.map(c => c.abs.toLong).toSeq
  RspTesterUtils.plot_fft(chiselFFTForPlot)
  
  //RspTesterUtils.checkError(scalaFFT, complexOut, tolerance = 2) // currently not usable

  stepToCompletion(silentFail = silentFail)
}

class SimpleChainInputSpec extends FlatSpec with Matchers {

  val params = SimpleChainInputParameters (
    fftParams = FFTParams.fixed(
      dataWidth = 16,
      twiddleWidth = 16,
      binPoint = 0, // added here!
      numPoints = 512,
      useBitReverse  = true,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(512))(0),
      keepMSBorLSB = Array.fill(log2Up(512))(true),
    ),
    fftAddress      = AddressSet(0x60000100, 0xFF),
    fftRAM          = AddressSet(0x60002000, 0xFFF),
    beatBytes      = 4)

  behavior of "SimpleChainInput"
  implicit val p: Parameters = Parameters.empty

  it should "work chain queue -> adapter -> fft" in {

  val lazyDut = LazyModule(new SimpleChainInput(params) with SimpleChainInputPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SimpleChainInput", "--top-name", "SimpleChainInput"), () => lazyDut.module) {
      c => new SimpleChainInputBlockTester(lazyDut, params, true)
    } should be (true)
  }

}












