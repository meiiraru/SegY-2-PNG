### **Seg-Y to PNG converter**

Command line application to convert SEG-Y files to PNG images

***

The output image follows the Petrel seismic color scheme

Scaling uses a bilinear interpolation algorithm

***

### **Command Line Arguments**
| Argument    | Flags        | Default      |
|-------------|--------------|--------------|
| Help        | -h, --help   | false        |
| Output File | -o, --output | ./output.png |
| Input File  | -f, --file   | ./input.segy |
| Amplitude   | --amplitude  | 0.0 0.0      |
| Scale       | -s, --scale  | 1.0 1.0      |

***

### **Requirements**

* Java 21+