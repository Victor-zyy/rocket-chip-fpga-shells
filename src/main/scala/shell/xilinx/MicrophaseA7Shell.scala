package sifive.fpgashells.shell.xilinx

import chisel3._
import chisel3.experimental.Analog
import chisel3.experimental.dataview._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._

import org.chipsalliance.cde.config._

import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxmicrophasea7mig._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._

/**
 * Microphase A7-LITE shell with NexysVideo-style DDR overlay.
 *
 * DDR note:
 * This version deliberately reuses the existing XilinxMicrophaseA7MIG diplomatic
 * wrapper because the A7-LITE DDR topology is also a single 16-bit DDR3 part.
 * Generate/configure the Vivado MIG IP with the A7-LITE parameters and pin XDC
 * supplied next to this file. If you later copy/rename the MIG device package,
 * replace XilinxMicrophaseA7MIG* with XilinxMicrophaseA7MIG* here.
 */

// -----------------------------------------------------------------------------
// 50 MHz system clock
// -----------------------------------------------------------------------------

class SysClockMicrophaseA7PlacedOverlay(
  val shell: MicrophaseA7ShellBasicOverlays,
  name: String,
  val designInput: ClockInputDesignInput,
  val shellInput: ClockInputShellInput)
  extends SingleEndedClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 50, jitterPS = 50) }

  shell { InModuleBody {
    val clk: Clock = io

    // A7-LITE schematic: CLK_50M -> FPGA pin J19.
    shell.xdc.addPackagePin(clk, "J19")
    shell.xdc.addIOStandard(clk, "LVCMOS33")
    shell.sdc.addClock("clk_50m", IOPin(clk), 50)
  } }
}

class SysClockMicrophaseA7ShellPlacer(
  val shell: MicrophaseA7ShellBasicOverlays,
  val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[MicrophaseA7ShellBasicOverlays]
{
  def place(designInput: ClockInputDesignInput) =
    new SysClockMicrophaseA7PlacedOverlay(shell, valName.name, designInput, shellInput)
}

// -----------------------------------------------------------------------------
// USB UART
// -----------------------------------------------------------------------------

class UARTMicrophaseA7PlacedOverlay(
  val shell: MicrophaseA7ShellBasicOverlays,
  name: String,
  val designInput: UARTDesignInput,
  val shellInput: UARTShellInput)
  extends UARTXilinxPlacedOverlay(name, designInput, shellInput, false)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq(
      ("U2", IOPin(io.rxd)), // Sch=UART_RX
      ("V2", IOPin(io.txd))  // Sch=UART_TX
    )

    packagePinsWithPackageIOs.foreach { case (pin, io) =>
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS33")
      shell.xdc.addIOB(io)
    }
  } }
}

class UARTMicrophaseA7ShellPlacer(
  val shell: MicrophaseA7ShellBasicOverlays,
  val shellInput: UARTShellInput)(implicit val valName: ValName)
  extends UARTShellPlacer[MicrophaseA7ShellBasicOverlays]
{
  def place(designInput: UARTDesignInput) =
    new UARTMicrophaseA7PlacedOverlay(shell, valName.name, designInput, shellInput)
}

// -----------------------------------------------------------------------------
// SPI (SD Card)
// -----------------------------------------------------------------------------

class SPIMicrophaseA7PlacedOverlay(
  val shell: MicrophaseA7ShellBasicOverlays,
  name: String,
  val designInput: SPIDesignInput,
  val shellInput: SPIShellInput)
  extends SPIPlacedOverlay(name, designInput, shellInput)
{
  shell { InModuleBody {
    val tlspiport = tlspiSink.bundle

    UIntToAnalog(tlspiport.sck, io.spi_clk, true.B)
    UIntToAnalog(tlspiport.dq(0).o, io.spi_cs, true.B)
    UIntToAnalog(tlspiport.cs(0), io.spi_dat(3), true.B)

    tlspiport.dq(0).i := false.B
    tlspiport.dq(1).i := AnalogToUInt(io.spi_dat(0)).asBool
    tlspiport.dq(2).i := false.B
    tlspiport.dq(3).i := false.B
  } }

  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq(
      ("U7",  IOPin(io.spi_clk)),    // SD_CLK
      ("AA8", IOPin(io.spi_cs)),     // SD_CMD / SPI MOSI
      ("W9",  IOPin(io.spi_dat(0))), // SD_DAT0 / SPI MISO
      ("Y9",  IOPin(io.spi_dat(1))), // SD_DAT1
      ("Y7",  IOPin(io.spi_dat(2))), // SD_DAT2
      ("Y8",  IOPin(io.spi_dat(3)))  // SD_DAT3 / SPI CS
    )

    packagePinsWithPackageIOs.foreach { case (pin, pinIO) =>
      shell.xdc.addPackagePin(pinIO, pin)
      shell.xdc.addIOStandard(pinIO, "LVCMOS33")
      shell.xdc.addIOB(pinIO)
    }
    packagePinsWithPackageIOs.drop(1).foreach { case (_, pinIO) =>
      shell.xdc.addPullup(pinIO)
    }
  } }
}

class SPIMicrophaseA7ShellPlacer(
  val shell: MicrophaseA7ShellBasicOverlays,
  val shellInput: SPIShellInput)(implicit val valName: ValName)
  extends SPIShellPlacer[MicrophaseA7ShellBasicOverlays]
{
  def place(designInput: SPIDesignInput): SPIMicrophaseA7PlacedOverlay =
    new SPIMicrophaseA7PlacedOverlay(shell, valName.name, designInput, shellInput)
}

// -----------------------------------------------------------------------------
// LEDs
// -----------------------------------------------------------------------------

object LEDMicrophaseA7PinConstraints {
  val pins = Seq(
    "M18", // LED1
    "N18"  // LED2
  )
}

class LEDMicrophaseA7PlacedOverlay(
  val shell: MicrophaseA7ShellBasicOverlays,
  name: String,
  val designInput: LEDDesignInput,
  val shellInput: LEDShellInput)
  extends LEDXilinxPlacedOverlay(
    name,
    designInput,
    shellInput,
    packagePin = Some(LEDMicrophaseA7PinConstraints.pins(shellInput.number))
  )

class LEDMicrophaseA7ShellPlacer(
  val shell: MicrophaseA7ShellBasicOverlays,
  val shellInput: LEDShellInput)(implicit val valName: ValName)
  extends LEDShellPlacer[MicrophaseA7ShellBasicOverlays]
{
  def place(designInput: LEDDesignInput) =
    new LEDMicrophaseA7PlacedOverlay(shell, valName.name, designInput, shellInput)
}

// -----------------------------------------------------------------------------
// DDR3
// -----------------------------------------------------------------------------

case object MicrophaseA7DDRSize extends Field[BigInt](0x20000000L) // 512 MiB
case object MicrophaseA7ShellDDR extends Field[Boolean](true)

class WithNoMicrophaseA7ShellDDR extends Config((site, here, up) => {
  case MicrophaseA7ShellDDR => false
})

class DDRMicrophaseA7PlacedOverlay(
  val shell: MicrophaseA7ShellBasicOverlays,
  name: String,
  val designInput: DDRDesignInput,
  val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxMicrophaseA7MIGPads](name, designInput, shellInput)
{
  val size = p(MicrophaseA7DDRSize)

  // A7-LITE MIG settings:
  // - DDR3 memory clock: 400 MHz
  // - PHY/controller ratio: 4:1
  // - MIG UI clock: 100 MHz
  // - MIG input/reference clocks: 200 MHz
  val ddrClkSys = shell { ClockSinkNode(freqMHz = 200) }
  val ddrGroup = shell { ClockGroup() }

  ddrClkSys := di.wrangler := ddrGroup := di.corePLL

  val migParams = XilinxMicrophaseA7MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxMicrophaseA7MIG(migParams))

  val ddrUI = shell { ClockSourceNode(freqMHz = 100) }
  val areset = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := di.wrangler := ddrUI

  def overlayOutput = DDROverlayOutput(ddr = mig.node)
  def ioFactory = new XilinxMicrophaseA7MIGPads(size)

  shell { InModuleBody {
    require(shell.sys_clock.get.isDefined, "Use of DDRMicrophaseA7PlacedOverlay depends on SysClockMicrophaseA7PlacedOverlay")

    val (ui, _) = ddrUI.out(0)
    val (dclkSys, _) = ddrClkSys.in(0)
    val (ar, _) = areset.in(0)
    val port = mig.module.io.port

    io <> port.viewAsSupertype(new XilinxMicrophaseA7MIGPads(mig.depth))

    ui.clock := port.ui_clk
    ui.reset := !port.mmcm_locked || port.ui_clk_sync_rst

    port.sys_clk_i := dclkSys.clock.asUInt
    port.sys_rst := !shell.pllReset
    port.aresetn := !(ar.reset.asBool)
  } }

  shell.sdc.addGroup(clocks = Seq("clk_pll_i"), pins = Seq(mig.island.module.blackbox.io.ui_clk))
}

class DDRMicrophaseA7ShellPlacer(
  val shell: MicrophaseA7ShellBasicOverlays,
  val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[MicrophaseA7ShellBasicOverlays]
{
  def place(designInput: DDRDesignInput) =
    new DDRMicrophaseA7PlacedOverlay(shell, valName.name, designInput, shellInput)
}

// -----------------------------------------------------------------------------
// Basic overlays
// -----------------------------------------------------------------------------

abstract class MicrophaseA7ShellBasicOverlays()(implicit p: Parameters)
  extends Series7Shell
{
  // Order matters: DDR depends on sys_clock.
  val sys_clock = Overlay(
    ClockInputOverlayKey,
    new SysClockMicrophaseA7ShellPlacer(this, ClockInputShellInput())
  )

  val ddr = if (p(MicrophaseA7ShellDDR)) Some(
    Overlay(DDROverlayKey, new DDRMicrophaseA7ShellPlacer(this, DDRShellInput()))
  ) else None

  val led = Seq.tabulate(2) { i =>
    Overlay(
      LEDOverlayKey,
      new LEDMicrophaseA7ShellPlacer(this, LEDMetas(i))(valName = ValName(s"led_$i"))
    )
  }

  val uart = Overlay(
    UARTOverlayKey,
    new UARTMicrophaseA7ShellPlacer(this, UARTShellInput())
  )

  val spi = Overlay(
    SPIOverlayKey,
    new SPIMicrophaseA7ShellPlacer(this, SPIShellInput())
  )

  def LEDMetas(i: Int): LEDShellInput =
    LEDShellInput(
      color = "green",
      rgb = false,
      number = i
    )
}

// -----------------------------------------------------------------------------
// Main shell
// -----------------------------------------------------------------------------

class MicrophaseA7Shell()(implicit p: Parameters)
  extends MicrophaseA7ShellBasicOverlays
{
  val resetPin = InModuleBody { Wire(Bool()) }
  val pllReset = InModuleBody { Wire(Bool()) }

  // Top-level GPIO LED port for optional software-controlled MMIO GPIO.
  val gpioLed = InModuleBody {
    IO(Analog(2.W)).suggestName("gpio_led")
  }

  val topDesign = LazyModule(p(DesignKey)(designParameters))

  p(ClockInputOverlayKey).foreach(_.place(ClockInputDesignInput()))

  override lazy val module = new Impl

  class Impl extends LazyRawModuleImp(this) {
    override def provideImplicitClockToLazyChildren = true

    // A7-LITE schematic: RESET -> FPGA pin L18, active-low button.
    val reset = IO(Input(Bool()))
    xdc.addPackagePin(IOPin(reset), "L18")
    xdc.addIOStandard(IOPin(reset), "LVCMOS33")
    xdc.addPullup(IOPin(reset))

    val resetIBUF = Module(new IBUF)
    resetIBUF.io.I := reset

    val sysclk: Clock = sys_clock.get() match {
      case Some(x: SysClockMicrophaseA7PlacedOverlay) => x.clock
      case other => throw new RuntimeException(s"Unexpected sys_clock overlay: $other")
    }

    val powerOnReset = PowerOnResetFPGAOnly(sysclk)
    sdc.addAsyncPath(Seq(powerOnReset))

    resetPin := resetIBUF.io.O
    pllReset := (!resetIBUF.io.O) || powerOnReset
  }
}
