package com.example.tco2display.legacy;

import com.google.gson.JsonElement;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.Query;
import java.util.Map;

public interface IntanglesApi {
    @GET("vehicle/fuel_consumed")
    Call<JsonElement> fuelConsumed(
            @HeaderMap Map<String, String> headers,
            @Query("pnum") int pnum,
            @Query("psize") int psize,
            @Query("no_default_fields") boolean noDefaultFields,
            @Query("proj") String proj,
            @Query("spec_ids") String specIds,
            @Query("groups") String groups,
            @Query("lastloc") boolean lastloc,
            @Query("acc_id") String accId,
            @Query("lang") String lang
    );
}
