set -e
if [ ! -f $INSTALL_DIR/bin/yosys ]; then
  mkdir -p $INSTALL_DIR
  git clone https://github.com/YosysHQ/yosys.git
  cd yosys
  make config-gcc
  sudo make install
  make test
fi
