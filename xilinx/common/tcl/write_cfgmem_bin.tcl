# See LICENSE for license details.
#
# Convert an existing bitstream into a raw SPI configuration-memory image.

if {$argc != 4} {
	puts {Usage: write_cfgmem_bin.tcl bitfile binfile interface size_mb}
	exit 1
}

lassign $argv bitfile binfile interface size_mb

if {![file exists $bitfile]} {
	puts "Error: bitstream does not exist: $bitfile"
	exit 1
}

write_cfgmem -force \
	-format bin \
	-interface $interface \
	-size $size_mb \
	-loadbit "up 0x0 $bitfile" \
	-file $binfile
