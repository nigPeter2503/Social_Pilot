package com.socialpilot.app.data.remote.oauth;

import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthHandoffExchangeRequest;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthStartRequest;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthStartResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface OAuthApi {
    @POST("api/oauth/facebook/start")
    Call<OAuthStartResponse> start(@Body OAuthStartRequest request);

    @POST("api/oauth/facebook/exchange")
    Call<OAuthExchangeResponse> exchange(@Body OAuthHandoffExchangeRequest request);
}
