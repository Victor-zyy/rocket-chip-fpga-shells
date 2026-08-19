package sifive.fpgashells.shell.xilinx

import chisel3._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import org.chipsalliance.cde.config._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxku5pmig._
import sifive.fpgashells.ip.xilinx.ku5pmig.Ku5pMIGDDRPads
import sifive.fpgashells.shell._

class SysClockKu5pPlacedOverlay(
  val shell: Ku5pShellBasicOverlays,
  name: String,
  val designInput: ClockInputDesignInput,
  val shellInput: ClockInputShellInput)
    extends SingleEndedClockInputXilinxPlacedOverlay(name, designInput, shellInput) {
  val node = shell { ClockSourceNode(freqMHz = 50.0, jitterPS = 50.0) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(IOPin(io), "E18")
    shell.xdc.addIOStandard(IOPin(io), "LVCMOS18")
  } }
}

class SysClockKu5pShellPlacer(
  val shell: Ku5pShellBasicOverlays,
  val shellInput: ClockInputShellInput)(implicit val valName: ValName)
    extends ClockInputShellPlacer[Ku5pShellBasicOverlays] {
  def place(designInput: ClockInputDesignInput) =
    new SysClockKu5pPlacedOverlay(shell, valName.name, designInput, shellInput)
}

class DDRRefClockKu5pPlacedOverlay(
  val shell: Ku5pShellBasicOverlays,
  name: String,
  val designInput: ClockInputDesignInput,
  val shellInput: ClockInputShellInput)
    extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput) {
  val node = shell { ClockSourceNode(freqMHz = 100.0, jitterPS = 50.0) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(IOPin(io.p), "T24")
    shell.xdc.addPackagePin(IOPin(io.n), "U24")
    shell.xdc.addIOStandard(IOPin(io.p), "DIFF_SSTL12")
    shell.xdc.addIOStandard(IOPin(io.n), "DIFF_SSTL12")
  } }
}

class DDRRefClockKu5pShellPlacer(
  val shell: Ku5pShellBasicOverlays,
  val shellInput: ClockInputShellInput)(implicit val valName: ValName)
    extends ClockInputShellPlacer[Ku5pShellBasicOverlays] {
  def place(designInput: ClockInputDesignInput) =
    new DDRRefClockKu5pPlacedOverlay(shell, valName.name, designInput, shellInput)
}

class UARTKu5pPlacedOverlay(
  val shell: Ku5pShellBasicOverlays,
  name: String,
  val designInput: UARTDesignInput,
  val shellInput: UARTShellInput)
    extends UARTXilinxPlacedOverlay(name, designInput, shellInput, false) {
  shell { InModuleBody {
    val pins = Seq(
      "H16" -> IOPin(io.rxd), // UART1_PC2FPGA
      "G16" -> IOPin(io.txd)  // UART1_FPGA2PC
    )
    pins.foreach { case (pin, port) =>
      shell.xdc.addPackagePin(port, pin)
      shell.xdc.addIOStandard(port, "LVCMOS18")
      shell.xdc.addIOB(port)
    }
  } }
}

class UARTKu5pShellPlacer(
  val shell: Ku5pShellBasicOverlays,
  val shellInput: UARTShellInput)(implicit val valName: ValName)
    extends UARTShellPlacer[Ku5pShellBasicOverlays] {
  def place(designInput: UARTDesignInput) =
    new UARTKu5pPlacedOverlay(shell, valName.name, designInput, shellInput)
}

class SDIOKu5pPlacedOverlay(
  val shell: Ku5pShellBasicOverlays,
  name: String,
  val designInput: SPIDesignInput,
  val shellInput: SPIShellInput)
    extends SDIOXilinxPlacedOverlay(name, designInput, shellInput) {
  shell { InModuleBody {
    // SD SPI mode: CLK, CMD/MOSI, DAT0/MISO and DAT3/nCS.
    val pins = Seq(
      "AE15" -> IOPin(io.spi_clk),
      "AD15" -> IOPin(io.spi_cs),
      "AF13" -> IOPin(io.spi_dat(0)),
      "AE13" -> IOPin(io.spi_dat(1)),
      "AF15" -> IOPin(io.spi_dat(2)),
      "AF14" -> IOPin(io.spi_dat(3))
    )
    pins.foreach { case (pin, port) =>
      shell.xdc.addPackagePin(port, pin)
      shell.xdc.addIOStandard(port, "LVCMOS33")
      shell.xdc.addIOB(port)
    }
    pins.drop(1).foreach { case (_, port) => shell.xdc.addPullup(port) }
  } }
}

class SDIOKu5pShellPlacer(
  val shell: Ku5pShellBasicOverlays,
  val shellInput: SPIShellInput)(implicit val valName: ValName)
    extends SPIShellPlacer[Ku5pShellBasicOverlays] {
  def place(designInput: SPIDesignInput) =
    new SDIOKu5pPlacedOverlay(shell, valName.name, designInput, shellInput)
}

object LEDKu5pPinConstraints {
  val pins = Seq("E16", "E17", "F15", "D15", "B16", "D18", "G15", "E22")
}

class LEDKu5pPlacedOverlay(
  val shell: Ku5pShellBasicOverlays,
  name: String,
  val designInput: LEDDesignInput,
  val shellInput: LEDShellInput)
    extends LEDXilinxPlacedOverlay(
      name,
      designInput,
      shellInput,
      packagePin = Some(LEDKu5pPinConstraints.pins(shellInput.number)),
      ioStandard = "LVCMOS18")

class LEDKu5pShellPlacer(
  val shell: Ku5pShellBasicOverlays,
  val shellInput: LEDShellInput)(implicit val valName: ValName)
    extends LEDShellPlacer[Ku5pShellBasicOverlays] {
  def place(designInput: LEDDesignInput) =
    new LEDKu5pPlacedOverlay(shell, valName.name, designInput, shellInput)
}

case object Ku5pDDRSize extends Field[BigInt](0x40000000L) // 1 GiB

class DDRKu5pPlacedOverlay(
  val shell: Ku5pShellBasicOverlays,
  name: String,
  val designInput: DDRDesignInput,
  val shellInput: DDRShellInput)
    extends DDRPlacedOverlay[Ku5pMIGDDRPads](name, designInput, shellInput) {
  val size = p(Ku5pDDRSize)
  val mig = LazyModule(
    new XilinxKu5pMIG(XilinxKu5pMIGParams(AddressSet.misaligned(di.baseAddress, size))))

  def overlayOutput = DDROverlayOutput(ddr = mig.node)
  def ioFactory = new Ku5pMIGDDRPads

  private val ddrUIClockNode = shell { ClockSourceNode(freqMHz = 200.0) }
  private val ddrBusAreset = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  private val ddrRefClockSink = shell { ClockSinkNode(freqMHz = 100.0) }

  require(shell.ddr_ref_clock.get().isDefined, "KU5P DDR requires the 100 MHz DDR reference clock overlay")
  shell { ddrRefClockSink := shell.ddr_ref_clock.get.get.overlayOutput.node }
  ddrBusAreset := designInput.wrangler := ddrUIClockNode

  val calibComplete = shell { InModuleBody { Wire(Bool()) } }

  shell { InModuleBody {
    val (refClock, _) = ddrRefClockSink.in.head
    val (uiClock, _) = ddrUIClockNode.out.head
    val (axiReset, _) = ddrBusAreset.in.head
    val aux = mig.module.auxio

    // DDR pads contain directed outputs and bidirectional Analog signals.
    // Connect the MIG child directly to the shell IO: BundleBridge performs
    // an internal bulk connect that cannot resolve the output directions.
    Ku5pMIGDDRPads.connect(io, mig.module.ddr4_port)

    uiClock.clock := aux.UIClock
    uiClock.reset := aux.UISyncedReset
    aux.sysClockInput := refClock.clock
    aux.sysResetInput := refClock.reset
    aux.AXIaresetn := !axiReset.reset.asBool
    calibComplete := aux.MIGCalibComplete

    def bind(port: Data, pins: Seq[String]): Unit =
      (IOPin.of(port) zip pins).foreach { case (ioPin, packagePin) =>
        shell.xdc.addPackagePin(ioPin, packagePin)
      }

    bind(io.ck_c, Seq("W26"))
    bind(io.ck_t, Seq("W25"))
    bind(io.cke, Seq("Y22"))
    bind(io.cs_n, Seq("Y26"))
    bind(io.act_n, Seq("V19"))
    bind(io.odt, Seq("AA25"))
    bind(io.adr, Seq(
      "P26", "P25", "R22", "AA24", "T23", "W20", "T22", "W19", "U21",
      "P21", "V22", "U19", "Y25", "P20", "Y23", "U26", "V26"))
    bind(io.ba, Seq("U22", "R26"))
    bind(io.bg, Seq("V21"))
    bind(io.reset_n, Seq("P23"))
    bind(io.dqs_c, Seq("AD26", "AB22", "AD18", "AC17"))
    bind(io.dqs_t, Seq("AC26", "AA22", "AC18", "AB17"))
    bind(io.dq, Seq(
      "AD24", "AF24", "AB26", "AB24", "AC24", "AB25", "AF25", "AD25",
      "AD23", "AE23", "AD21", "AC23", "AC22", "AE21", "AB21", "AC21",
      "AF17", "AE17", "AC19", "AF18", "AF19", "AD19", "AE16", "AD16",
      "AB20", "AB19", "AA19", "AA20", "Y17", "AA17", "Y18", "AA18"))
    bind(io.dm_dbi_n, Seq("AE25", "AE22", "AD20", "Y20"))

    IOPin.of(io.ck_t).foreach(shell.xdc.addIOStandard(_, "DIFF_SSTL12_DCI"))
  } }
}

class DDRKu5pShellPlacer(
  val shell: Ku5pShellBasicOverlays,
  val shellInput: DDRShellInput)(implicit val valName: ValName)
    extends DDRShellPlacer[Ku5pShellBasicOverlays] {
  def place(designInput: DDRDesignInput) =
    new DDRKu5pPlacedOverlay(shell, valName.name, designInput, shellInput)
}

abstract class Ku5pShellBasicOverlays()(implicit p: Parameters) extends UltraScaleShell {
  val pllReset = InModuleBody { Wire(Bool()) }

  val sys_clock = Overlay(
    ClockInputOverlayKey,
    new SysClockKu5pShellPlacer(this, ClockInputShellInput())(ValName("clk")))
  val ddr_ref_clock = Overlay(
    ClockInputOverlayKey,
    new DDRRefClockKu5pShellPlacer(this, ClockInputShellInput())(ValName("c0_sys_clk")))
  val uart = Overlay(UARTOverlayKey, new UARTKu5pShellPlacer(this, UARTShellInput()))
  val sdio = Overlay(SPIOverlayKey, new SDIOKu5pShellPlacer(this, SPIShellInput()))
  val led = Seq.tabulate(8) { i =>
    Overlay(
      LEDOverlayKey,
      new LEDKu5pShellPlacer(this, LEDShellInput(color = "red", number = i))(ValName(s"led_$i")))
  }
  val ddr = Overlay(DDROverlayKey, new DDRKu5pShellPlacer(this, DDRShellInput()))
}
