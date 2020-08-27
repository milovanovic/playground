if [ ! -f $INSTALL_DIR/openlane ]; then
  mkdir -p $INSTALL_DIR
  #git clone git@github.com:efabless/openlane --branch rc2
  git clone https://github.com/efabless/openlane --branch rc2
  cd openlane/docker_build
  make merge
  cd ..
fi
