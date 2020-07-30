set -e
if [ ! -f $INSTALL_DIR/bin/iverilog ]; then
  mkdir -p $INSTALL_DIR
  sudo apt-get install -y gperf
  git clone https://github.com/steveicarus/iverilog
  cd iverilog
  sh autoconf.sh
  ./configure
  make
  make check
  sudo make install
fi
