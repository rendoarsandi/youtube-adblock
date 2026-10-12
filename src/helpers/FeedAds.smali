# Version-specific helper for com.google.android.youtube 21.40.161.
# Own patch code. Relies on exact guarded Google DEX types, not reflection.
.class public final Lapp/local/youtube/adblock/FeedAds;
.super Ljava/lang/Object;

.method public static patch(Lakvx;)V
    .registers 6
    if-eqz p0, :done
    invoke-virtual {p0}, Lakvx;->H()Lbawu;
    move-result-object v0
    if-eqz v0, :done

    # Copy outer protobuf before changing its child reference.
    invoke-virtual {v0}, Lbawu;->copyOnWrite()V
    iget-object v1, v0, Lbawu;->instance:Lbaxc;
    instance-of v2, v1, Lcom/google/protos/youtube/api/innertube/InnertubeContext$InnerTubeContext;
    if-eqz v2, :done
    check-cast v1, Lcom/google/protos/youtube/api/innertube/InnertubeContext$InnerTubeContext;
    iget-object v2, v1, Lcom/google/protos/youtube/api/innertube/InnertubeContext$InnerTubeContext;->c:Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;
    if-eqz v2, :done

    # Child may be shared with non-browse/search requests. Clone via its builder.
    invoke-virtual {v2}, Lbaxc;->toBuilder()Lbawu;
    move-result-object v2
    invoke-virtual {v2}, Lbawu;->copyOnWrite()V
    iget-object v3, v2, Lbawu;->instance:Lbaxc;
    check-cast v3, Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;
    const-string v4, "Android Automotive"
    iput-object v4, v3, Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;->z:Ljava/lang/String;
    iget v4, v3, Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;->c:I
    or-int/lit16 v4, v4, 0x2000
    iput v4, v3, Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;->c:I
    invoke-virtual {v2}, Lbawu;->build()Lbaxc;
    move-result-object v2
    check-cast v2, Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;
    iput-object v2, v1, Lcom/google/protos/youtube/api/innertube/InnertubeContext$InnerTubeContext;->c:Lcom/google/protos/youtube/api/innertube/InnertubeContext$ClientInfo;
    iget v2, v1, Lcom/google/protos/youtube/api/innertube/InnertubeContext$InnerTubeContext;->b:I
    or-int/lit8 v2, v2, 0x1
    iput v2, v1, Lcom/google/protos/youtube/api/innertube/InnertubeContext$InnerTubeContext;->b:I
    :done
    return-void
.end method
