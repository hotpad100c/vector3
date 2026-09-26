package ml.mypals.vectorthree.flashback.fade;

public record Fade(float red, float green, float blue, float opacity) {
    public Fade lerp(Fade to, float amount) {
        return new Fade(red + (to.red - red) * amount, green + (to.green - green) * amount,
                blue + (to.blue - blue) * amount, opacity + (to.opacity - opacity) * amount);
    }
}
