package com.texify.backend.exception;

public class OAuth2AccountException extends RuntimeException {

    public OAuth2AccountException(String provider) {
        super("This account uses " + provider + " to sign in. Please use the social login button.");
    }
}
