package simpleChain

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


import java.io._

trait SimpleChainPins extends SimpleChain {
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

class SimpleChainBlockTester
(
  dut: SimpleChain with SimpleChainPins,
  params: SimpleChainParameters,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.in)

  def asNdigitBinary (source: Int, digits: Int): String = {
    val lstring = source.toBinaryString
    //val sign = if (source > 0) "%0" else "%1"
    if (source >= 0) {
      val l: java.lang.Long = lstring.toLong
      String.format ("%0" + digits + "d", l)
    }
    else
      lstring.takeRight(digits)
  }

  def getSimpleTone(numSamples: Int, f1r: Double): Seq[Int] = {
    require(f1r != 0, "Digital frequency should not be zero!")
    import scala.util.Random
    
    (0 until numSamples).map(i => (math.sin(2 * math.Pi * f1r * i)*scala.math.pow(2, 32-1)).toInt & 0xFFFF0000)
  }

  // format input data according to axi bus
  val inData = getSimpleTone(numSamples = params.fftParams.numPoints, f1r = 0.03567/8)
  
  println("Input data is:")
  inData.map(c => println(c.toString))
  
  val scalaFFT = fourierTr(DenseVector(inData.toArray)).toScalaVector
  val scalaPlot = scalaFFT.map(c => c.abs.toLong).toSeq

  def plot_scala(chiselFFT: Seq[Long]): Unit = {

    val f = Figure()
    val p = f.subplot(0)
    p.legend_=(true)
    
    val data = chiselFFT.map(e => e.toDouble).toSeq
    val xaxis = (0 until data.length).map(e => e.toDouble).toSeq.toArray
    
    p += plot(xaxis, data.toArray, name = "scala FFT")
   
    p.ylim(data.min, data.max)
    p.title_=(s"FFT with length ${chiselFFT.length}")

    p.xlabel = "Frequency Bin"
    p.ylabel = "Amplitude"//"20log10(||Vpeak||)"
    //f.saveas(s"test_run_dir/RSPBlock/parallel_in_scala_fft.pdf")
  }
  plot_scala(scalaPlot)

  def plot_data (chiselFFT: Seq[Int]): Unit = {
    import breeze.linalg._
    import breeze.plot._

    val f = Figure()
    val p = f.subplot(0)
    p.legend_=(true)
    
    val data = chiselFFT.map(e => e.toDouble).toSeq
    val xaxis = (0 until data.length).map(e => e.toDouble).toSeq.toArray
    
    p += plot(xaxis, data.toArray, name = "parallel in")
   
    p.ylim(data.min, data.max)
    p.title_=(s"FFT with length ${chiselFFT.length}")

    p.xlabel = "Frequency Bin"
    p.ylabel = "Amplitude"//"20log10(||Vpeak||)"
    //f.saveas(s"test_run_dir/RSPBlock/parallel_in_fft.pdf")
  }

  plot_data(inData)

  var dataByte = Seq[Int]()
  for (i <- inData) {
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
    dataByte = dataByte :+ ((i >>> 16) & 0xFF)
    dataByte = dataByte :+ ((i >>> 24) & 0xFF)
  }

  // write to file
//   val file = new File("test_run_dir/RSPBlock/data_in.txt")
//   val bw = new BufferedWriter(new FileWriter(file))
//   for (line <- inData) {
//     bw.write(f"0x${line}%08X"+"\n")
//   }
//   bw.close()

  step(1)
   // add master transactions
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))

//   var outputSeq = Seq[Int]()
//   var temp: Long = 0
//   var i = 0
//   while (outputSeq.length < params.fftParams.numPoints) {
//     if (peek(dut.out.valid)==1) {
//       temp = temp + (peek(dut.out.bits.data).toLong << ((i % params.beatBytes)*8))
//       if ((i % params.beatBytes) == params.beatBytes-1) {
//         outputSeq = outputSeq :+ temp//.asUInt(32.W)
//         temp = 0
//       }
//       i = i + 1
//     }
//     step(1)
//   }

  stepToCompletion(silentFail = silentFail)
}

class SimpleChainSpec extends FlatSpec with Matchers {

  val params = SimpleChainParameters (
    fftParams = FFTParams.fixed(
      dataWidth = 16,
      twiddleWidth = 16,
      binPoint = 14, // added here!
      numPoints = 32,
      useBitReverse  = true,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(32))(0),
      keepMSBorLSB = Array.fill(log2Up(32))(true),
    ),
    fftAddress      = AddressSet(0x60000100, 0xFF),
    fftRAM          = AddressSet(0x60002000, 0xFFF),
    beatBytes      = 4)

  behavior of "SimpleChain"
  implicit val p: Parameters = Parameters.empty

  it should "work chain queue -> adapter -> fft" in {

  val lazyDut = LazyModule(new SimpleChain(params) with SimpleChainPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SimpleChain", "--top-name", "SimpleChain"), () => lazyDut.module) {
      c => new SimpleChainBlockTester(lazyDut, params, true)
    } should be (true)
  }

}












