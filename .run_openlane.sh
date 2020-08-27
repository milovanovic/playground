#!/bin/bash
set -ev

# Setup docker container
#docker run -it -v $(pwd):/openLANE_flow -v $PDK_ROOT:$PDK_ROOT -e PDK_ROOT=$PDK_ROOT -u $(id -u $USER):$(id -g $USER) openlane:rc2
#docker run -v $(pwd):/openLANE_flow -v $PDK_ROOT:$PDK_ROOT -e PDK_ROOT=$PDK_ROOT -u $(id -u $USER):$(id -g $USER) openlane:rc2 "flow.tcl -design spm"
docker exec -e PDK_ROOT=$PDK_ROOT -u $(id -u $USER):$(id -g $USER) openlane:rc2 /bin/sh -c "./flow.tcl -design spm"
# Run flow for design SPM as test
#source flow.tcl -design spm
#exit

# Copy GDS file to transfer.sh
gds_file="designs/spm/runs/*/results/magic/spm.gds"
curl -n -T $gds_file https://transfer.sh
