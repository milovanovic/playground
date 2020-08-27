# Install skywater-pdk (https://github.com/google/skywater-pdk)
if [ ! -f $INSTALL_DIR/skywater-pdk ]; then
  mkdir -p $INSTALL_DIR
  # Setup skywater-pdk
  #git clone git@github.com:google/skywater-pdk.git
  git clone https://github.com/google/skywater-pdk
  cd skywater-pdk
  git checkout 4e5e318e0cc578090e1ae7d6f2cb1ec99f363120
  git submodule update --init libraries/sky130_fd_sc_hd/latest
  make sky130_fd_sc_hd
  # Setup the configurations and tech files for Magic, Netgen, OpenLANE using open_pdks
  cd $INSTALL_DIR
  # git clone git clone git@github.com:efabless/open_pdks.git -b rc2
  git clone https://github.com/efabless/open_pdks -b rc2
  cd open_pdks
  make
  make install-local
fi
