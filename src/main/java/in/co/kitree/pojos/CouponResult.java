package in.co.kitree.pojos;


public class CouponResult {
    private boolean valid;
    private String message;
    private double discount;
    private double newAmount;

    public CouponResult() {
    }


    public CouponResult(boolean valid, String message, double discount, double newAmount) {
        this.valid = valid;
        this.message = message;
        this.discount = discount;
        this.newAmount = newAmount;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public double getNewAmount() {
        return newAmount;
    }

    public void setNewAmount(double newAmount) {
        this.newAmount = newAmount;
    }

    // --- Compatibility methods for new tests ---
    public int getDiscountAmount() {
        return (int) discount;
    }
    public int getFinalAmount() {
        return (int) newAmount;
    }
    public String getError() {
        return message;
    }
    public void setError(String error) {
        this.message = error;
    }
}
