# See LICENSE for license details.
set name {ku5p}
set part_fpga {xcku5p-ffvb676-2-i}
set part_board {}
set bootrom_inst {rom}

# Match the vendor KU5P flow: emit a raw SPI configuration image together
# with the normal .bit file for the on-board MT25QU128 configuration flash.
set generate_bin_file true
