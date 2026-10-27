package world.sirius.order.services;

import java.util.UUID;

public class OrderNotFoundException extends Exception {

    private String orderId;
    
    public OrderNotFoundException(UUID id) {
        orderId = id.toString();
    }

    @Override
    public String getLocalizedMessage() {
        return super.getLocalizedMessage();
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    public String toString() {
        return super.toString();
    }
    
}
