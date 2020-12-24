#!/bin/bash

src_files=$(find . -type f \( -iname \*.scala -o -iname \*.c -o -iname \*.h -o -iname \*.cpp -o -iname \*.v -o -iname \*.vhdl -o -iname \*.tcl -o  -iname \*py -o -iname \*.mat -o -iname \*.sbt \))

for src_file in $src_files; do
  [[ ! -e $src_file ]] && continue
  echo $src_file
  # here check does file already contain identifier, if it contains then check extension and add it appropriately
  if  grep -q "SPDX-License-Identifier: Apache-2.0" $src_file; then
    file_name=$(basename $src_file)
    echo "File ${file_name} already contains licence identifier"
  else
    case "$src_file" in
      *.py | *.tcl) sed -i '1i # SPDX-License-Identifier: Apache-2.0 \n' $src_file ;;
      *.vhd) sed -i '1i -- SPDX-License-Identifier: Apache-2.0 \n' $src_file ;;
      *.v | *.cpp | *.h | *.scala | *.sbt) sed -i '1i // SPDX-License-Identifier: Apache-2.0 \n' $src_file ;;
      *.mat) sed -i '1i % SPDX-License-Identifier: Apache-2.0 \n' $src_file ;;
      *.) : ;;
    esac
  fi
done
