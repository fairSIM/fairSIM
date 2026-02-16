package org.fairsim.fiji;

import ij.IJ;
import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.process.ShortProcessor;
import ij.ImageStack;

import org.fairsim.utils.SimpleMT;

public class CoinFlipImage implements PlugIn {

    /**
     * Mutable state holder for xorshift_star.
     */
    private static class XorShiftState {
        long seed;
        long value;
    }

    /**
     * xorshift* PRNG implementation with additional multiplication.
     * Updates the state in-place and sets the generated value.
     */
    private static void xorshift_star(XorShiftState state) {
        long x = state.seed;
        if (x == 0) {
            x = 0xdeadbeefcafebabeL; // default seed if zero
        }
        x ^= (x >> 12);
        x ^= (x << 25);
        x ^= (x >> 27);
        state.seed = x;
        state.value = x * 2685821657736338717L;
    }

    @Override
    public void run(String arg) {

        ImagePlus im = IJ.getImage();
        if (im == null) {
            IJ.showMessage("No image open");
            return;
        }

        final int width = im.getWidth();
        final int height = im.getHeight();

        ImageStack stack = im.getStack();
        ImageStack[] outputStack = new ImageStack[2];
        for (int i = 0; i < 2; i++) {
            outputStack[i] = new ImageStack(width, height);
        }

        // initialize PRNG states for each pixel
        long start = System.nanoTime();
        XorShiftState stateInit = new XorShiftState();
        stateInit.seed = 123456789; // fixed seed for reproducibility
        XorShiftState[] prngState = new XorShiftState[width * height];

        for (int i = 0; i < width * height; i++) {
            prngState[i] = new XorShiftState();
            xorshift_star(stateInit);
            prngState[i].seed = stateInit.value; // simple seed based on pixel index
        }

        long end = System.nanoTime();

        IJ.log(String.format("PRNG initialization time (ms): %.2f", (end - start) / 1_000_000.0));

        // process each slice
        for (int s = 1; s <= stack.getSize(); s++) {

            im.setSlice(s);
            ShortProcessor sp = (ShortProcessor) im.getStack().getProcessor(s).convertToShortProcessor();
            short[] pixels = (short[]) sp.getPixels();

            short[][] imgs = new short[2][];
            imgs[0] = new short[width * height];
            imgs[1] = new short[width * height];

            start = System.nanoTime();
            new SimpleMT.PFor(0, width * height) {
                @Override
                public void at(int i, int threadID) {
                    if (threadID == 0 && i % width == 0) {
                        IJ.showProgress(i, width * height);
                    }
                }

                @Override
                public void at(int i) {
                    int count = pixels[i] & 0xFFFF; // interpret as unsigned short
                    for (int j = 0; j < count; j++) {
                        xorshift_star(prngState[i]);
                        if (prngState[i].value < 0) {
                            imgs[0][i]++;
                        } else {
                            imgs[1][i]++;
                        }
                    }
                }
            };

            end = System.nanoTime();
            IJ.log(String.format("Slice %d coin flip processing time (ms): %.2f", s, (end - start) / 1_000_000.0));

            // create output images
            ShortProcessor sp1 = new ShortProcessor(width, height);
            ShortProcessor sp2 = new ShortProcessor(width, height);
            short[] outPixels1 = (short[]) sp1.getPixels();
            short[] outPixels2 = (short[]) sp2.getPixels();
            System.arraycopy(imgs[0], 0, outPixels1, 0, width * height);
            System.arraycopy(imgs[1], 0, outPixels2, 0, width * height);

            outputStack[0].addSlice(stack.getSliceLabel(s), sp1);
            outputStack[1].addSlice(stack.getSliceLabel(s), sp2);
        }

        ImagePlus outIm1 = new ImagePlus(im.getTitle() + "_coinflip_heads", outputStack[0]);
        outIm1.show();
        ImagePlus outIm2 = new ImagePlus(im.getTitle() + "_coinflip_tails", outputStack[1]);
        outIm2.show();

    }

    public static void main(String[] arg) {
        new ij.ImageJ(ij.ImageJ.EMBEDDED);
        ImagePlus ip = IJ.openImage(arg[0]);
        ip.show();

        CoinFlipImage plugin = new CoinFlipImage();
        plugin.run("");
    }

}
