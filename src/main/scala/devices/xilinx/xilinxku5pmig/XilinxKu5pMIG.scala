package sifive.fpgashells.devices.xilinx.xilinxku5pmig

import chisel3._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{CacheBlockBytes, CrossesToOnlyOneClockDomain}
import freechips.rocketchip.tilelink.{TLBuffer, TLInwardNode, TLToAXI4}
import org.chipsalliance.cde.config.Parameters
import sifive.fpgashells.ip.xilinx.ku5pmig._

case class XilinxKu5pMIGParams(address: Seq[AddressSet])

class XilinxKu5pMIGAuxIO extends Bundle {
  val sysClockInput = Input(Clock())
  val sysResetInput = Input(Reset())
  val AXIaresetn = Input(Bool())
  val UIClock = Output(Clock())
  val UISyncedReset = Output(Reset())
  val MIGCalibComplete = Output(Bool())
}

class XilinxKu5pMIGIsland(c: XilinxKu5pMIGParams)(implicit p: Parameters)
    extends LazyModule
    with CrossesToOnlyOneClockDomain {
  private val ranges = AddressRange.fromSets(c.address)
  require(ranges.size == 1, "DDR range must be contiguous")
  require(ranges.head.size <= 0x40000000L, "KU5P MIG supports at most 1 GiB")
  private val offset = ranges.head.base

  override val crossing: ClockCrossingType = AsynchronousCrossing(8)

  val device = new MemoryDevice
  val node = AXI4SlaveNode(
    Seq(
      AXI4SlavePortParameters(
        slaves = Seq(
          AXI4SlaveParameters(
            address = c.address,
            resources = device.reg,
            regionType = RegionType.UNCACHED,
            executable = true,
            supportsWrite = TransferSizes(1, 256 * 8),
            supportsRead = TransferSizes(1, 256 * 8)
          )
        ),
        beatBytes = 8
      )
    )
  )

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val auxio = IO(new XilinxKu5pMIGAuxIO)
    val ddr4_port = IO(new Ku5pMIGDDRPads)

    val blackbox = Module(new ku5pmig)
    private val (axi, _) = node.in.head

    Ku5pMIGDDRPads.connect(ddr4_port, blackbox.c0_ddr4_intf)
    blackbox.c0_sys_clk_i := auxio.sysClockInput
    blackbox.sys_rst := auxio.sysResetInput
    blackbox.c0_ddr4_aresetn := auxio.AXIaresetn
    auxio.UIClock := blackbox.c0_ddr4_ui_clk
    auxio.UISyncedReset := blackbox.c0_ddr4_ui_clk_sync_rst
    auxio.MIGCalibComplete := blackbox.c0_init_calib_complete

    val mappedWAddr = axi.aw.bits.addr - offset.U
    val mappedRAddr = axi.ar.bits.addr - offset.U

    blackbox.c0_ddr4_s_axi_awvalid := axi.aw.valid
    axi.aw.ready := blackbox.c0_ddr4_s_axi_awready
    blackbox.c0_ddr4_s_axi_awid := axi.aw.bits.id
    blackbox.c0_ddr4_s_axi_awaddr := mappedWAddr
    blackbox.c0_ddr4_s_axi_awlen := axi.aw.bits.len
    blackbox.c0_ddr4_s_axi_awsize := axi.aw.bits.size
    blackbox.c0_ddr4_s_axi_awburst := axi.aw.bits.burst
    blackbox.c0_ddr4_s_axi_awlock := axi.aw.bits.lock
    blackbox.c0_ddr4_s_axi_awcache := "b0011".U
    blackbox.c0_ddr4_s_axi_awprot := axi.aw.bits.prot
    blackbox.c0_ddr4_s_axi_awqos := axi.aw.bits.qos

    blackbox.c0_ddr4_s_axi_wvalid := axi.w.valid
    axi.w.ready := blackbox.c0_ddr4_s_axi_wready
    blackbox.c0_ddr4_s_axi_wdata := axi.w.bits.data
    blackbox.c0_ddr4_s_axi_wstrb := axi.w.bits.strb
    blackbox.c0_ddr4_s_axi_wlast := axi.w.bits.last

    blackbox.c0_ddr4_s_axi_bready := axi.b.ready
    axi.b.valid := blackbox.c0_ddr4_s_axi_bvalid
    axi.b.bits.id := blackbox.c0_ddr4_s_axi_bid
    axi.b.bits.resp := blackbox.c0_ddr4_s_axi_bresp

    blackbox.c0_ddr4_s_axi_arvalid := axi.ar.valid
    axi.ar.ready := blackbox.c0_ddr4_s_axi_arready
    blackbox.c0_ddr4_s_axi_arid := axi.ar.bits.id
    blackbox.c0_ddr4_s_axi_araddr := mappedRAddr
    blackbox.c0_ddr4_s_axi_arlen := axi.ar.bits.len
    blackbox.c0_ddr4_s_axi_arsize := axi.ar.bits.size
    blackbox.c0_ddr4_s_axi_arburst := axi.ar.bits.burst
    blackbox.c0_ddr4_s_axi_arlock := axi.ar.bits.lock
    blackbox.c0_ddr4_s_axi_arcache := "b0011".U
    blackbox.c0_ddr4_s_axi_arprot := axi.ar.bits.prot
    blackbox.c0_ddr4_s_axi_arqos := axi.ar.bits.qos

    blackbox.c0_ddr4_s_axi_rready := axi.r.ready
    axi.r.valid := blackbox.c0_ddr4_s_axi_rvalid
    axi.r.bits.data := blackbox.c0_ddr4_s_axi_rdata
    axi.r.bits.resp := blackbox.c0_ddr4_s_axi_rresp
    axi.r.bits.id := blackbox.c0_ddr4_s_axi_rid
    axi.r.bits.last := blackbox.c0_ddr4_s_axi_rlast
  }
}

class XilinxKu5pMIG(c: XilinxKu5pMIGParams)(implicit p: Parameters) extends LazyModule {
  private val buffer = LazyModule(new TLBuffer())
  private val toAXI4 = LazyModule(new TLToAXI4(adapterName = Some("mem")))
  private val indexer = LazyModule(new AXI4IdIndexer(idBits = 4))
  private val deinterleaver = LazyModule(new AXI4Deinterleaver(p(CacheBlockBytes)))
  private val yanker = LazyModule(new AXI4UserYanker())
  val island = LazyModule(new XilinxKu5pMIGIsland(c))

  val node: TLInwardNode = buffer.node
  island.crossAXI4In(island.node) := yanker.node := deinterleaver.node := indexer.node := toAXI4.node := buffer.node

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val auxio = IO(new XilinxKu5pMIGAuxIO)
    val ddr4_port = IO(new Ku5pMIGDDRPads).suggestName("c0_ddr4")

    auxio <> island.module.auxio
    Ku5pMIGDDRPads.connect(ddr4_port, island.module.ddr4_port)

    island.module.clock := auxio.UIClock
    island.module.reset := auxio.UISyncedReset
  }
}
