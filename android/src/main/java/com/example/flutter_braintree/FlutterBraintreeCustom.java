package com.example.flutter_braintree;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.CardClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.CardTokenizeCallback;
import com.braintreepayments.api.PayPalAccountNonce;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.PayPalClient;
import com.braintreepayments.api.PayPalListener;
import com.braintreepayments.api.PayPalVaultRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.UserCanceledException;

import java.util.HashMap;

public class FlutterBraintreeCustom extends AppCompatActivity implements PayPalListener {
    private BraintreeClient braintreeClient;
    private PayPalClient payPalClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);

        try {
            Intent intent = getIntent();
            String authorization = intent.getStringExtra("authorization");
            if (authorization == null) {
                throw new Exception("Authorization token is required.");
            }

            braintreeClient = new BraintreeClient(this, authorization);
            String type = intent.getStringExtra("type");
            if (type == null) {
                throw new Exception("Request type is required.");
            }

            switch (type) {
                case "tokenizeCreditCard":
                    tokenizeCreditCard();
                    break;
                case "requestPaypalNonce":
                    payPalClient = new PayPalClient(this, braintreeClient);
                    payPalClient.setListener(this);
                    requestPaypalNonce();
                    break;
                default:
                    throw new Exception("Invalid request type: " + type);
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        setIntent(newIntent);
    }

    private void tokenizeCreditCard() {
        Intent intent = getIntent();
        Card card = new Card();
        card.setExpirationMonth(intent.getStringExtra("expirationMonth"));
        card.setExpirationYear(intent.getStringExtra("expirationYear"));
        card.setCvv(intent.getStringExtra("cvv"));
        card.setCardholderName(intent.getStringExtra("cardholderName"));
        card.setNumber(intent.getStringExtra("cardNumber"));

        CardClient cardClient = new CardClient(braintreeClient);
        cardClient.tokenize(card, new CardTokenizeCallback() {
            @Override
            public void onResult(CardNonce cardNonce, Exception error) {
                if (cardNonce != null) {
                    onPaymentMethodNonceCreated(cardNonce);
                } else if (error != null) {
                    onError(error);
                }
            }
        });
    }

    private void requestPaypalNonce() {
        Intent intent = getIntent();
        if (intent.getStringExtra("amount") == null) {
            // Vault flow
            PayPalVaultRequest vaultRequest = new PayPalVaultRequest();
            vaultRequest.setDisplayName(intent.getStringExtra("displayName"));
            vaultRequest.setBillingAgreementDescription(intent.getStringExtra("billingAgreementDescription"));
            payPalClient.tokenizePayPalAccount(this, vaultRequest);
        } else {
            // Checkout flow
            PayPalCheckoutRequest checkOutRequest = new PayPalCheckoutRequest(intent.getStringExtra("amount"));
            checkOutRequest.setCurrencyCode(intent.getStringExtra("currencyCode"));
            payPalClient.tokenizePayPalAccount(this, checkOutRequest);
        }
    }

    private void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        HashMap<String, Object> nonceMap = new HashMap<>();
        nonceMap.put("nonce", paymentMethodNonce.getString());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());

        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
            nonceMap.put("paypalPayerId", paypalAccountNonce.getPayerId());
            nonceMap.put("typeLabel", "PayPal");
            nonceMap.put("description", paypalAccountNonce.getEmail());
        } else if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
            nonceMap.put("typeLabel", cardNonce.getCardType());
            nonceMap.put("description", "ending in ••" + cardNonce.getLastTwo());
        }

        Intent result = new Intent();
        result.putExtra("type", "paymentMethodNonce");
        result.putExtra("paymentMethodNonce", nonceMap);
        setResult(RESULT_OK, result);
        finish();
    }

    private void onCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void onError(Exception error) {
        Intent result = new Intent();
        result.putExtra("error", error);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    private void handleError(Exception e) {
        Intent result = new Intent();
        result.putExtra("error", e);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    @Override
    public void onPayPalSuccess(@NonNull PayPalAccountNonce payPalAccountNonce) {
        onPaymentMethodNonceCreated(payPalAccountNonce);
    }

    @Override
    public void onPayPalFailure(@NonNull Exception error) {
        if (error instanceof UserCanceledException) {
            if (((UserCanceledException) error).isExplicitCancelation()) {
                onCancel();
            }
        } else {
            onError(error);
        }
    }
}