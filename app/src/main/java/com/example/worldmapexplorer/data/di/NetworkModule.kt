package com.example.worldmapexplorer.data.di

import com.example.worldmapexplorer.data.network.CoordinatesAdapter
import com.example.worldmapexplorer.data.network.api.ElevationAPi
import com.example.worldmapexplorer.data.network.api.GeocodingApi
import com.example.worldmapexplorer.data.network.api.GeometryApi
import com.skydoves.sandwich.adapters.ApiResponseCallAdapterFactory
import com.example.worldmapexplorer.data.network.api.NominatimApi
import com.example.worldmapexplorer.data.network.api.RouterApi
import com.example.worldmapexplorer.data.network.client.ElevationClient
import com.example.worldmapexplorer.data.network.client.NominatimClient
import com.example.worldmapexplorer.data.network.client.RouteClient
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val request = originalRequest.newBuilder().url(originalRequest.url).build()
        Timber.d(request.toString())
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Set connection timeout
            .readTimeout(30, TimeUnit.SECONDS)    // Set read timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MapExplorer/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatimApi(okHttpClient: OkHttpClient): NominatimApi {
        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()
            .create(NominatimApi::class.java)
    }


    @Provides
    @Singleton
    fun provideGeocodingApi(okHttpClient: OkHttpClient): GeocodingApi {
        val moshi = Moshi.Builder()
            .add(CoordinatesAdapter()) // Register the custom adapter
            .build()

        return Retrofit.Builder()
            .baseUrl("https://nominatim.geocoding.ai")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()
            .create(GeocodingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeometryApi(okHttpClient: OkHttpClient): GeometryApi {
        return Retrofit.Builder()
            .baseUrl("https://overpass-api.de/api/")
            .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()
            .create(GeometryApi::class.java)
    }


    @Provides
    @Singleton
    fun provideValhallaApi(okHttpClient: OkHttpClient): RouterApi {
        return Retrofit.Builder()
            .baseUrl("https://valhalla1.openstreetmap.de/")
            .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()
            .create(RouterApi::class.java)
    }

    @Provides
    @Singleton
    fun provideElevationApi(okHttpClient: OkHttpClient): ElevationAPi {
        return Retrofit.Builder()
            .baseUrl("https://api.open-elevation.com/api/v1/")
            .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()
            .create(ElevationAPi::class.java)
    }


    @Provides
    @Singleton
    fun provideRouteClient(routerApi: RouterApi): RouteClient {
        return RouteClient(routerApi)
    }

    @Provides
    @Singleton
    fun provideElevationClient(elevationAPi: ElevationAPi): ElevationClient {
        return ElevationClient(elevationAPi)
    }

    @Provides
    @Singleton
    fun provideNominatimClient(nominatimApi: NominatimApi, geometryApi: GeometryApi,geocodingApi: GeocodingApi): NominatimClient {
        return NominatimClient(nominatimApi,geometryApi,geocodingApi)
    }

}