# Install skywater-pdk (https://github.com/google/skywater-pdk)
if [ ! -f $INSTALL_DIR/skywater-pdk ]; then
  mkdir -p $INSTALL_DIR
  git clone git@github.com:google/skywater-pdk.git
  cd skywater-pdk
  git checkout 4e5e318e0cc578090e1ae7d6f2cb1ec99f363120
  git submodule update --init libraries/sky130_fd_sc_hd/latest
  make sky130_fd_sc_hd
fi
