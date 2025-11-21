### **Seg-Y to PNG converter**

Command line application to convert SEG-Y files to PNG images

***

The output image follows the Petrel seismic color scheme

Scaling uses a bilinear interpolation algorithm

***

### **Arguments**
1. "path": Path to the SEG-Y file (**required!!**)
2. "scale X": Scale factor for the output image width (default: 1)
3. "scale Y": Scale factor for the output image height (default: 1)

Output is saved in the working directory of this script, with same filename as the input file (with the .png extension)

***

### **Requirements**

* Java 21+