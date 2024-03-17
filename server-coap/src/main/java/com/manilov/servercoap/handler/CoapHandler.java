package com.manilov.servercoap.handler;

import com.manilov.servercoap.service.MetricService;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CoapHandler extends CoapResource {

    private final MetricService metricService;

    public CoapHandler(MetricService metricService) {
        super("metrics");
        getAttributes().setTitle("CoAP Resource");
        this.metricService = metricService;
    }

    @Override
    public void handlePOST(CoapExchange exchange) {
        long time = Long.parseLong(exchange.getRequestText());
        metricService.updateDelay(time);
        String response = "Received POST request with payload: " + time;
        exchange.respond(response);
    }
}

