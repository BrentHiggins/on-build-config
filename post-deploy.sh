#!/bin/bash
echo "Running post-deploy script"


#raw sol logs saved in WORKSPACE/build-deps
#this script convert them into html by ansi2html tool
#and move them to 'build' folder for publishing
cd $WORKSPACE/build-deps
for file in `ls *sol.log.raw`; do
    ansi2html < $file > $WORKSPACE/build-deps/${file%.*}
done
