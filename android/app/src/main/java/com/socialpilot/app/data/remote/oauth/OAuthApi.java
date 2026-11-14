package com.socialpilot.app.data.remote.oauth;

import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthHandoffExchangeRequest;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthStartRequest;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthStartResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface OAuthApi {
    @POST("api/oauth/{provider}/start")
    Call<OAuthStartResponse> start(@Path("provider") String provider, @Body OAuthStartRequest request);

    @POST("api/oauth/{provider}/exchange")
    Call<OAuthExchangeResponse> exchange(@Path("provider") String provider, @Body OAuthHandoffExchangeRequest request);
}
