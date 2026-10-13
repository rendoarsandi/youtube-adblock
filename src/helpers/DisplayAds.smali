# Exact-version bridge. Stock construction/cleanup completes before this call.
.class public final Lapp/local/youtube/adblock/DisplayAds;
.super Ljava/lang/Object;

.method public static filter(Lhgo;Lhgt;Lwpp;)Lhgo;
    .registers 7
    if-eqz p0, :done
    if-eqz p1, :done
    if-eqz p2, :done
    :try_start
    iget-object v0, p2, Lwpp;->o:Ljava/lang/String;
    iget-object v1, p2, Lwpp;->m:Ljava/lang/StringBuilder;
    if-eqz v1, :done
    invoke-static {v0, v1}, Lapp/local/youtube/adblock/DisplayAdPolicy;->shouldHide(Ljava/lang/String;Ljava/lang/StringBuilder;)Z
    move-result v2
    if-eqz v2, :done
    invoke-static {p1}, Lhqn;->aE(Lhgt;)Lhqm;
    move-result-object v2
    iget-object v2, v2, Lhqm;->a:Lhqn;
    if-eqz v2, :done
    invoke-static {}, Lapp/local/youtube/adblock/LocalDiagnostics;->adHidden()V
    move-object p0, v2
    :try_end
    .catch Ljava/lang/Exception; {:try_start .. :try_end} :failed
    :done
    return-object p0
    :failed
    move-exception v0
    invoke-static {}, Lapp/local/youtube/adblock/LocalDiagnostics;->filterFailed()V
    return-object p0
.end method
