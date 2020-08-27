set -e
# Install Yosys (https://github.com/YosysHQ/yosys)
if [ ! -f $INSTALL_DIR/bin/yosys ]; then
  mkdir -p $INSTALL_DIR
  git clone https://github.com/YosysHQ/yosys.git
  cd yosys
  git pull
  git checkout yosys-0.9
  make
  make test
  make PREFIX=$INSTALL_DIR install
fi
