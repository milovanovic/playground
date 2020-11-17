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
import magnitude._

import java.io._

trait SimpleChainOutputPins extends SimpleChainOutput {
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
    BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 4)) :=
    ioInNode

  val in = InModuleBody { ioInNode.makeIO() }
  val out = InModuleBody { ioOutNode.makeIO() }
}


class SimpleChainOutputBlockTester
(
  dut: SimpleChainOutput with SimpleChainOutputPins,
  params: SimpleChainOutputParameters,
  selMux: Int,
  sendComplex: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.in)
  val numPoints = params.fftParams.numPoints
  val dataWidth = params.fftParams.protoIQ.getWidth
  
  
  // sel = 0 -> passthrough
  // sel = 1 -> magsqr
  // sel = 2 -> jplMagOut
  // sel = 3 -> log2MagOut
  
  //val inData = RspTesterUtils.genRandSignal(numSamples = numPoints, numPoints)// for sqr mag
  val inData = if (selMux == 1) RspTesterUtils.getTone(numSamples = params.fftParams.numPoints, 0.03125, numPoints) else RspTesterUtils.getTone(numSamples = params.fftParams.numPoints, 0.03125)
  
  println("Input data is:")
  inData.map(c => println(c.toString))
  inData.map(c => println(RspTesterUtils.asNdigitBinary(c, 16)))
  
  val fftScala = fourierTr(DenseVector(inData.toArray)).toScalaVector
  
 println("Expected result of the fft should be: ")

  val expectedDataReal = selMux match {
      case 1  => fftScala.map(c => (c.real/numPoints) * (c.real/numPoints) + (c.imag/numPoints) * (c.imag/numPoints))
      case 2  => fftScala.map(c => c.abs/numPoints)
      case 3  => fftScala.map(c => RspTesterUtils.log2((c.abs/numPoints).toDouble).toInt)
      case _  => inData
  }
  
  //expectedDataReal.map (c => println(c.toString))
  // Expand test to use complex data as an input
  //println("Expected result of the fft should be: ")
  fftScala.map(c => println((c/numPoints).toString))
  val absFFTScala = fftScala.map(c => c.abs.toLong).toSeq
  absFFTScala.map(c => println((c/numPoints).toString))
  

  RspTesterUtils.plot_data(inData)
  // RspTesterUtils.plot_fft(absFFTScala)
  // println("Expected magnitude of the fft should be: ")
  // absFFTScala.map(c => println((c/numPoints).toString))
  
  // real sinusoid
  val axi4StreamIn = RspTesterUtils.formAXI4StreamRealData(inData, 16)
  //axi4StreamIn.foreach(c => println(c.toString))
  
  step(1)
  poke(dut.out.ready, true.B) // make output always ready to accept data
  
  memWriteWord(params.logMagMuxAddress.base, selMux)
  
  // add master transactions
  // master.addTransactions((0 until axi4StreamIn.size).map(i => AXI4StreamTransaction(data = axi4StreamIn(i))))
  // master.addTransactions((0 until axi4StreamIn.size).map(i => AXI4StreamTransaction(data = axi4StreamIn(i))))
  master.addTransactions((0 until axi4StreamIn.size).map(i => AXI4StreamTransaction(data = axi4StreamIn(i))))
  master.addTransactions((0 until axi4StreamIn.size).map(i => AXI4StreamTransaction(data = axi4StreamIn(i))))
  master.addTransactions((0 until axi4StreamIn.size).map(i => AXI4StreamTransaction(data = axi4StreamIn(i))))
  master.addTransactions(axi4StreamIn.zipWithIndex.map { case (data, idx) => AXI4StreamTransaction(data = data,  last = if (idx == axi4StreamIn.length - 1) true else false) })
  
  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  
  // check only one fft window 
  while (outSeq.length < numPoints * 4 * 4) {
    if (peek(dut.out.valid) == 1 && peek(dut.out.ready) == 1) {
      peekedVal = peek(dut.out.bits.data)
      outSeq = outSeq :+ peekedVal.toInt
    }
    step(1)
  }
  
//   println("Output is:")
//   outSeq.foreach (c => println(RspTesterUtils.asNdigitBinary(c, 8)))
//   val mag = outSeq.sliding(outSeq.length, 4)

  var realSeq = Seq[Int]()
  var imagSeq = Seq[Int]()
  var tmpReal: Short = 0
  var tmpImag: Short = 0
  
  for (i <- 0 until outSeq.length by 4) {
  
   // println(RspTesterUtils.asNdigitBinary(outSeq(i + 2), 8))
    tmpReal = java.lang.Integer.parseInt(
                    RspTesterUtils.asNdigitBinary(outSeq(i + 3), 8)
                    ++ RspTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    tmpImag = java.lang.Long.parseLong(
                    RspTesterUtils.asNdigitBinary(outSeq(i + 1), 8)
                    ++ RspTesterUtils.asNdigitBinary(outSeq(i), 8), 2
                ).toShort
    realSeq = realSeq :+ tmpReal.toInt
    imagSeq = imagSeq :+ tmpImag.toInt
  }
  
  if (selMux == 0) {
    val complexOut = realSeq.zip(imagSeq).map { case (real, imag) => Complex(real, imag) }
    val absFFTChisel = complexOut.map(c => c.abs.toLong).toSeq
    if (numPoints > 256) {
      RspTesterUtils.plot_fft(absFFTChisel, "Chisel")
    }
    val complexScala = fftScala.map(c => Complex((c.real/numPoints), c.imag/numPoints))
    RspTesterUtils.checkFFTError(complexScala , complexOut, tolerance = 4)
  }
  else {
    val magScala = fftScala.map(c => Complex(c.real/numPoints, c.imag/numPoints).abs.toInt).toSeq
    if (numPoints > 256) {
      RspTesterUtils.plot_fft(realSeq.map(c => c.toLong), "Chisel")
    }
    if (selMux == 2) {
      //magScala ++ magScala ++ magScala ++ magScala
      RspTesterUtils.checkMagError(magScala ++ magScala ++ magScala ++ magScala, realSeq, tolerance = 4)
    }
  }
  
  step (100)
  stepToCompletion(silentFail = silentFail)
}

class SimpleChainOutputSpec extends FlatSpec with Matchers {
   
  implicit val p: Parameters = Parameters.empty
  val selMux = 2 // jpl mag
  
  behavior of "Chain:  fft -> logMagMux -> buffer -> adapter"
  
  for (i <- Seq(16, 32, 64, 128, 256, 512, 1024)) {
    for (decType <- Seq(DIFDecimType, DITDecimType)) {
      it should f"compute radix 2^2 $decType FFT, size $i with no growing logic, connected with logMagMux and adapter 32b/8b" in {
        val params = SimpleChainOutputParameters (
          fftParams = FFTParams.fixed(
            dataWidth = 16,
            twiddleWidth = 16,
            numPoints = i,
            useBitReverse  = true, //true
            decimType = decType,
            numAddPipes = 1,
            numMulPipes = 1,
            expandLogic = Array.fill(log2Up(i))(0),
            keepMSBorLSB = Array.fill(log2Up(i))(true),
          ),
          fftAddress      = AddressSet(0x60000100, 0xFF),
          fftRAM          = AddressSet(0x60002000, 0xFFF),
          logMagMuxParams = MAGParams.fixed(
            dataWidth       = 16,
            binPoint        = 0,
            dataWidthLog    = 16,
            binPointLog     = 11,
            log2LookUpWidth = 11,
            useLast         = true,
            numAddPipes     = 1,
            numMulPipes     = 1
          ),
          logMagMuxAddress  = AddressSet(0x60000080, 0xF),
          beatBytes         = 4)
      
      val lazyDut = LazyModule(new SimpleChainOutput(params) with SimpleChainOutputPins)
      
      chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SimpleChainOutput", "--top-name", "SimpleChainOutputDIF"), () => lazyDut.module) {
        c => new SimpleChainOutputBlockTester(lazyDut, params, selMux, true)
      } should be (true)
      }
    }
  }

/*  
  it should "work chain fft -> logmagmux -> queue -> adapter 32/8 - DIF fft algorithm" in {
  
  val paramsDIT = SimpleChainOutputParameters (
    fftParams = FFTParams.fixed(
      dataWidth = 16,
      twiddleWidth = 16,
      numPoints = 8,
      useBitReverse  = true, //true
      decimType = DITDecimType,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(8))(0),
      keepMSBorLSB = Array.fill(log2Up(8))(true),
    ),
    fftAddress      = AddressSet(0x60000100, 0xFF),
    fftRAM          = AddressSet(0x60002000, 0xFFF),
    logMagMuxParams = MAGParams.fixed(
      dataWidth       = 16,
      binPoint        = 0,
      dataWidthLog    = 16,
      binPointLog     = 11,
      log2LookUpWidth = 11,
      useLast         = true,
      numAddPipes     = 1,
      numMulPipes     = 1
    ),
    logMagMuxAddress  = AddressSet(0x60000080, 0xF),
    beatBytes         = 4)
  
  
  
  it should "work chain fft -> logmagmux -> queue -> adapter 32/8 - DIT fft algorithm" in {
  
  val lazyDutDIT = LazyModule(new SimpleChainOutput(paramsDIT) with SimpleChainOutputPins)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv", "--target-dir", "test_run_dir/SimpleChainOutput", "--top-name", "SimpleChainOutputDIT"), () => lazyDutDIT.module) {
      c => new SimpleChainOutputBlockTester(lazyDutDIT, paramsDIT, selMux, true)
    } should be (true)
  }*/
}

