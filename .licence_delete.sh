#!/bin/bash

src_files=$(find . -type f \( -iname \*.scala -o -iname \*.c -o -iname \*.h -o -iname \*.cpp -o -iname \*.v -o -iname \*.vhdl -o -iname \*.tcl -o  -iname \*py -o -iname \*.mat -o -iname \*.sbt \))

for src_file in $src_files; do
  [[ ! -e $src_file ]] && continue
  echo $src_file
  sed -i '/SPDX-License-Identifier: Apache-2.0/d' $src_file && sed -i '1{/^$/d}' $src_file
done
