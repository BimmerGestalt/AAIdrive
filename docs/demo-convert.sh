name=`basename "$1" .mp4`
ffmpeg -i $name.mp4 -filter_complex "[0:v] split [a][b];[a] palettegen [p];[b]fifo[c];[c][p] paletteuse" $name.gif
