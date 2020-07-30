set -e
# Install Iverilog (https://github.com/steveicarus/iverilog)
if [ ! -f $INSTALL_DIR/bin/iverilog ]; then
  mkdir -p $INSTALL_DIR
  git clone https://github.com/steveicarus/iverilog
  cd iverilog
  git pull
  #git checkout v0_9-branch
  sh autoconf.sh
  ./configure
  make
  make check
  sudo make install
fi
