#!/bin/bash
set -ev

# Setup docker container
#docker run -it -v $(pwd):/openLANE_flow -v $PDK_ROOT:$PDK_ROOT -e PDK_ROOT=$PDK_ROOT -u $(id -u $USER):$(id -g $USER) openlane:rc2
#docker run -v $(pwd):/openLANE_flow -v $PDK_ROOT:$PDK_ROOT -e PDK_ROOT=$PDK_ROOT -u $(id -u $USER):$(id -g $USER) openlane:rc2 "flow.tcl -design spm"
##ls $PDK_ROOT
##ls $TRAVIS_BUILD_DIR/openlane
##ls $(pwd)/openlane
##ls $INSTALL_DIR
#ls $INSTALL_DIR/sky130A
#ls $INSTALL_DIR/sky130A/libs.ref
#ls $INSTALL_DIR/sky130A/libs.ref/sky130_fd_sc_hd
#ls $INSTALL_DIR/sky130A/libs.ref/sky130_fd_sc_hd/lef
pwd
find -name "*.lef"
docker run -v $(pwd):/openLANE_flow -v $PDK_ROOT:$PDK_ROOT -e PDK_ROOT=$PDK_ROOT -u $(id -u $USER):$(id -g $USER) openlane:rc2 /bin/sh -c "ls openlane; ls /openLANE_flow; find -name '*.lef'; openlane/flow.tcl -design spm"
# ls $INSTALL_DIR/sky130A/libs.ref/sky130_fd_sc_hd/lef;
# Run flow for design SPM as test
#source flow.tcl -design spm
#exit

# Copy GDS file to transfer.sh
gds_file="designs/spm/runs/2*/results/magic/spm.gds"
curl -n -T $gds_file https://transfer.sh
