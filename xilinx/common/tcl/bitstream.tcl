# See LICENSE for license details.

# Write a bitstream for the current design. Boards which set
# generate_bin_file also receive a raw configuration image suitable for their
# SPI configuration flash.
set bitfile [file join $wrkdir "${top}.bit"]
if {[info exists generate_bin_file] && $generate_bin_file} {
	write_bitstream -force $bitfile -bin_file
} else {
	write_bitstream -force $bitfile
}

# Save the timing delays for cells in the design in SDF format
write_sdf -force [file join $wrkdir "${top}.sdf"]

# Export the current netlist in verilog format
write_verilog -mode timesim -force [file join ${wrkdir} "${top}.v"]
