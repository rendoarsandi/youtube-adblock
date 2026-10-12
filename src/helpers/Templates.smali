.class public Lapp/local/youtube/adblock/Templates;
.super Ljava/lang/Object;

.method public static i0()V
 .registers 12
invoke-static {v2, v6}, Lapp/revanced/extension/shared/spoof/StandaloneDefaults;->filterPlayerRequest(Ljava/lang/String;Ljava/util/Map;)Ljava/lang/String;
move-result-object v2
return-void
.end method

.method public static i1()V
 .registers 8
invoke-static {v0}, Lapp/revanced/extension/shared/spoof/SpoofVideoStreamsPatch;->blockInitPlaybackRequest(Ljava/lang/String;)Ljava/lang/String;
move-result-object v0
return-void
.end method

.method public static i2()V
 .registers 6
invoke-direct {v4, v0}, Lakul;->patch_setStreamingData(Lbgna;)V
return-void
.end method

.method public static i3()V
 .registers 25
move-object v0, v10
iget-object v1, v0, Ldjs;->a:Landroid/net/Uri;
iget v2, v0, Ldjs;->d:I
iget-object v3, v0, Ldjs;->e:[B
invoke-static {v1, v2, v3}, Lapp/revanced/extension/shared/spoof/SpoofVideoStreamsPatch;->removeVideoPlaybackPostBody(Landroid/net/Uri;I[B)[B
move-result-object v1
iput-object v1, v0, Ldjs;->e:[B
return-void
.end method

.method public static i4()V
 .registers 44
const/4 v15, 0x0
return-void
.end method

.method public static i5()V
 .registers 7
const/4 v6, 0x0
return-void
.end method

.method public static i6()V
 .registers 25
const/16 v8, 0x0
return-void
.end method

.method public static i7()Lcom/google/android/libraries/youtube/media/player/exo2/platypus/PlatypusEnablingManager$DecisionWithReason;
 .registers 4
sget-object v0, Lcom/google/android/libraries/youtube/media/player/exo2/platypus/PlatypusEnablingManager$DecisionWithReason;->e:Lcom/google/android/libraries/youtube/media/player/exo2/platypus/PlatypusEnablingManager$DecisionWithReason;
return-object v0
.end method

.method private final streaming(Lbgna;)V
    .registers 9
    if-eqz v8, :disabled
    iget-object v2, v8, Lbgna;->c:Ljava/lang/String;
    if-eqz v2, :disabled
    invoke-static {v2}, Lapp/revanced/extension/shared/spoof/SpoofVideoStreamsPatch;->getStreamingData(Ljava/lang/String;)[B
    move-result-object v3
    if-eqz v3, :disabled
    sget-object v4, Lbgmw;->a:Lbgmw;
    invoke-static {v4, v3}, Lbaxc;->parseFrom(Lbaxc;[B)Lbaxc;
    move-result-object v5
    check-cast v5, Lbgmw;
    iget-object v6, v5, Lbgmw;->h:Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass$StreamingData;
    if-eqz v6, :disabled
    iput-object v6, v7, Lakul;->a:Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass$StreamingData;
    :disabled
    return-void
.end method
