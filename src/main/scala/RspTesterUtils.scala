package rspTesting

import breeze.math.Complex
import breeze.linalg._
import breeze.plot._

object RspTesterUtils {
    
  def asNdigitBinary (source: Int, digits: Int): String = {
    val lstring = source.toBinaryString
    //val sign = if (source > 0) "%0" else "%1"
    if (source >= 0) {
      //val l: java.lang.Long = lstring.toLong
      val l: java.lang.Long = lstring.toLong
      String.format ("%0" + digits + "d", l)
    }
    else
      lstring.takeRight(digits)
  }
  
  // assumption is that dataWidth of the fft inpur is always equal to 16
  // generates real sinusoid
  // scale parameter is useful when square magnitude is calculated to prevent overflow!
  def getTone(numSamples: Int, f1r: Double, scale: Int = 1): Seq[Int] = {
    (0 until numSamples).map(i => (math.sin(2 * math.Pi * f1r * i) * scala.math.pow(2, 14)/scale).toInt)
  }
  
  def getComplexTone(numSamples: Int, f1r: Double, scale: Int = 1): Seq[Complex] = {
    (0 until numSamples).map(i => Complex((math.sin(2 * math.Pi * f1r * i) * scala.math.pow(2, 13)/scale).toInt, (math.sin(2 * math.Pi * f1r * i)*scala.math.pow(2, 13)/scale).toInt))
  }
  
  // TODO: Generate also negative data
  def genRandSignal(numSamples: Int, scale: Int = 1): Seq[Int] = {
    import scala.math.sqrt
    import scala.util.Random
    
    Random.setSeed(11110L)
    (0 until numSamples).map(x => (Random.nextDouble()*scala.math.pow(2, 14)/scale).toInt)
  }
  
  //TODO: Parametrize dataWidth
  def genComplexRandSignal(numSamples: Int, scale: Int = 1): Seq[Complex] = {
    import scala.math.sqrt
    import scala.util.Random
    
    Random.setSeed(11110L)
    (0 until numSamples).map(x => Complex((Random.nextDouble()*scala.math.pow(2, 13)/scale).toInt, Random.nextDouble()*scala.math.pow(2, 13)/scale.toInt))
  }
  
  // assumed that output axi4 streaming data has dataWidth equal to 32
  def formAXI4StreamRealData(inData: Seq[Int], dataWidth: Int): Seq[Int] = {
    inData.map(data => java.lang.Long.parseLong(
                                  asNdigitBinary(data, dataWidth) ++ 
                                  asNdigitBinary(0, dataWidth), 2).toInt)
  }
     
  def formAXI4StreamComplexData(inData : Seq[Complex], dataWidth: Int): Seq[Int] = {
    inData.map(data => java.lang.Integer.parseInt(
                                  asNdigitBinary(data.real.toInt, dataWidth) ++ 
                                  asNdigitBinary(data.imag.toInt, dataWidth)))
  }
  def log2(x: Double): Double =  scala.math.log(x)/scala.math.log(2)

  def plot_data(inputData: Seq[Int]): Unit = {

    val f = Figure()
    val p = f.subplot(0)
    p.legend_=(true)
    
    val data = inputData.map(e => e.toDouble).toSeq
    val xaxis = (0 until data.length).map(e => e.toDouble).toSeq.toArray
    
    p += plot(xaxis, data.toArray, name = "input test signal")
   
    p.ylim(data.min, data.max)
    p.title_=(s"Test signal - ${inputData.length} time samples")

    p.xlabel = "Time Bins"
    p.ylabel = "Amplitude"//"20log10(||Vpeak||)"
    f.saveas(s"test_run_dir/inputData.pdf")
  }

  def plot_fft(fftRes: Seq[Long], name: String = "scala"): Unit = {

    val f = Figure()
    val p = f.subplot(0)
    p.legend_=(true)
    
    val data = fftRes.map(e => e.toDouble).toSeq
    val xaxis = (0 until data.length).map(e => e.toDouble).toSeq.toArray
    
    p += plot(xaxis, data.toArray, name = name ++ " FFT")
   
    p.ylim(data.min, data.max)
    p.title_=(s"FFT - length ${fftRes.length}")

    p.xlabel = "Frequency Bin"
    p.ylabel = "Amplitude"//"20log10(||Vpeak||)"
    f.saveas(s"test_run_dir/" ++ "fft" ++ "name" ++ ".pdf")
  }
  
  // TODO: Add function for plotting Chisel and Scala fft at the same graph
  
  def checkFFTError(expected: Seq[Complex], received: Seq[Complex], tolerance: Int = 2) {
    expected.zip(received).foreach {
      case (in, out) =>
        require(math.abs(in.real - out.real) <= tolerance & math.abs(in.imag - out.imag) <= tolerance, "Tolerance is not satisfied")
    }
  }
  
  def checkMagError(expected: Seq[Int], received: Seq[Int], tolerance: Int = 2) {
    expected.zip(received).foreach {
      case (in, out) =>
        require(math.abs(in - out) <= tolerance, "Tolerance is not satisfied")
    }
  }
}
