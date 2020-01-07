package com.sap.cloud.security.servlet;

import com.sap.cloud.security.token.SecurityContext;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.ValidationResult;
import com.sap.cloud.security.token.validation.Validator;
import com.sap.cloud.security.xsuaa.client.OAuth2TokenKeyServiceWithCache;
import com.sap.cloud.security.xsuaa.client.OidcConfigurationServiceWithCache;
import com.sap.cloud.security.xsuaa.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

public abstract class AbstractTokenAuthenticator implements TokenAuthenticator {

	private static final Logger logger = LoggerFactory.getLogger(AbstractTokenAuthenticator.class);
	private Validator<Token> tokenValidator;
	protected OidcConfigurationServiceWithCache oidcConfigurationService;
	protected OAuth2TokenKeyServiceWithCache tokenKeyService;

	@Override
	public TokenAuthenticationResult validateRequest(ServletRequest request, ServletResponse response) {
		if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			HttpServletResponse httpResponse = (HttpServletResponse) response;
			String authorizationHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
			if (headerIsAvailable(authorizationHeader)) {
				try {
					Token token = getTokenExtractor().from(authorizationHeader);
					ValidationResult result = createTokenValidator().validate(token);
					if (result.isValid()) {
						SecurityContext.setToken(token);
						return authenticated(token);
					} else {
						return unauthenticated(httpResponse,
								"Error during token validation: " + result.getErrorDescription());
					}
				} catch (Exception e) {
					return unauthenticated(httpResponse, "Unexpected error occurred: " + e.getMessage());
				}
			} else {
				return unauthenticated(httpResponse, "Authorization header is missing.");
			}
		}
		return TokenAuthenticationResult.createUnauthenticated("Could not process request " + request);
	}

	public AbstractTokenAuthenticator withOidcConfigurationService(
			OidcConfigurationServiceWithCache oidcConfigurationService) {
		this.oidcConfigurationService = oidcConfigurationService;
		return this;
	}

	public AbstractTokenAuthenticator withOAuth2TokenKeyService(OAuth2TokenKeyServiceWithCache tokenKeyService) {
		this.tokenKeyService = tokenKeyService;
		return this;
	}

	protected abstract Validator<Token> createTokenValidator();

	protected Validator<Token> getOrCreateTokenValidator() {
		if (tokenValidator == null) {
			tokenValidator = createTokenValidator();
		}
		return tokenValidator;
	}

	private TokenAuthenticationResult unauthenticated(HttpServletResponse httpResponse, String message) {
		try {
			httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
		} catch (IOException e) {
			logger.error("Could not send unauthenticated response!", e);
		}
		return TokenAuthenticationResult.createUnauthenticated(message);
	}

	protected TokenAuthenticationResult authenticated(Token token) {
		return TokenAuthenticationResult.authenticated(Collections.emptyList(), token);
	}

	private boolean headerIsAvailable(String authorizationHeader) {
		return authorizationHeader != null && !authorizationHeader.isEmpty();
	}

}
