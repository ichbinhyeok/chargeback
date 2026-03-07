package com.example.demo.dispute.api;

import com.example.demo.dispute.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/lemonsqueezy")
public class LemonWebhookController {

    private final PaymentService paymentService;

    public LemonWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<String> handle(
            @RequestBody String payload,
            @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        paymentService.processLemonWebhook(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
