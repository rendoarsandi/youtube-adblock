# Uses the exact APK's own video/image ad classification; unknown inputs fail open.
.class public final Lapp/local/youtube/adblock/ShortsAds;
.super Ljava/lang/Object;

.method public static isAd(Lbdfp;)Z
    .registers 3
    if-eqz p0, :ordinary
    :try_start
    invoke-static {p0}, Laqpp;->ad(Lbdfp;)Z
    move-result v0
    if-nez v0, :ad
    sget-object v0, Lbkrv;->b:Lbaxa;
    invoke-virtual {p0, v0}, Lbawx;->c(Lbawl;)Z
    move-result v1
    if-eqz v1, :ordinary
    invoke-virtual {p0, v0}, Lbawx;->b(Lbawl;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lbkrv;
    invoke-static {v0}, Laqpp;->I(Lbkrv;)Z
    move-result v0
    return v0
    :try_end
    .catch Ljava/lang/Exception; {:try_start .. :try_end} :failed
    :ad
    const/4 v0, 0x1
    return v0
    :failed
    move-exception v0
    :ordinary
    const/4 v0, 0x0
    return v0
.end method

# Paired append sites call this immediately before the native conditional skip.
.method public static skip(Lbdfp;)Z
    .registers 2
    invoke-static {p0}, Lapp/local/youtube/adblock/ShortsAds;->isAd(Lbdfp;)Z
    move-result v0
    if-eqz v0, :done
    invoke-static {v0}, Lapp/local/youtube/adblock/LocalDiagnostics;->shortsEntriesFiltered(I)V
    :done
    return v0
.end method

.method public static filter(Ljava/util/List;)Ljava/util/List;
    .registers 8
    if-nez p0, :try_start
    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
    move-result-object v0
    return-object v0
    :try_start
    const/4 v0, 0x0
    const/4 v1, 0x0
    invoke-interface {p0}, Ljava/util/List;->size()I
    move-result v2
    :loop
    if-ge v1, v2, :complete
    invoke-interface {p0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
    move-result-object v3
    instance-of v4, v3, Lbdfp;
    if-eqz v4, :keep
    move-object v5, v3
    check-cast v5, Lbdfp;
    invoke-static {v5}, Lapp/local/youtube/adblock/ShortsAds;->isAd(Lbdfp;)Z
    move-result v4
    if-eqz v4, :keep
    if-nez v0, :next
    const/4 v4, 0x0
    invoke-interface {p0, v4, v1}, Ljava/util/List;->subList(II)Ljava/util/List;
    move-result-object v5
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0, v5}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
    goto :next
    :keep
    if-eqz v0, :next
    invoke-interface {v0, v3}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    :next
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :complete
    if-eqz v0, :unchanged
    invoke-interface {v0}, Ljava/util/List;->size()I
    move-result v1
    sub-int v1, v2, v1
    invoke-static {v1}, Lapp/local/youtube/adblock/LocalDiagnostics;->shortsEntriesFiltered(I)V
    :try_end
    .catch Ljava/lang/Exception; {:try_start .. :try_end} :failed
    return-object v0
    :failed
    move-exception v0
    :unchanged
    return-object p0
.end method

# Original lists are returned on unknown shape or failure. No shared list mutates.
.method public static filterPair(Ljava/util/List;Ljava/util/List;)[Ljava/util/List;
    .registers 10
    const/4 v0, 0x2
    new-array v0, v0, [Ljava/util/List;
    const/4 v1, 0x0
    aput-object p0, v0, v1
    const/4 v1, 0x1
    aput-object p1, v0, v1
    if-eqz p0, :done
    if-eqz p1, :done
    :try_start
    invoke-interface {p0}, Ljava/util/List;->size()I
    move-result v2
    invoke-interface {p1}, Ljava/util/List;->size()I
    move-result v3
    if-ne v2, v3, :done
    const/4 v1, 0x0
    const/4 v5, 0x0
    const/4 v6, 0x0
    :loop
    if-ge v1, v2, :complete
    invoke-interface {p0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
    move-result-object v3
    instance-of v4, v3, Lbdfp;
    if-eqz v4, :keep
    move-object v7, v3
    check-cast v7, Lbdfp;
    invoke-static {v7}, Lapp/local/youtube/adblock/ShortsAds;->isAd(Lbdfp;)Z
    move-result v4
    if-eqz v4, :keep
    if-nez v5, :next
    const/4 v4, 0x0
    invoke-interface {p0, v4, v1}, Ljava/util/List;->subList(II)Ljava/util/List;
    move-result-object v7
    new-instance v5, Ljava/util/ArrayList;
    invoke-direct {v5, v7}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
    invoke-interface {p1, v4, v1}, Ljava/util/List;->subList(II)Ljava/util/List;
    move-result-object v7
    new-instance v6, Ljava/util/ArrayList;
    invoke-direct {v6, v7}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
    goto :next
    :keep
    if-eqz v5, :next
    invoke-interface {v5, v3}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    invoke-interface {p1, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
    move-result-object v7
    invoke-interface {v6, v7}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    :next
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :complete
    if-eqz v5, :done
    invoke-static {v5}, Ljava/util/Collections;->unmodifiableList(Ljava/util/List;)Ljava/util/List;
    move-result-object v5
    invoke-static {v6}, Ljava/util/Collections;->unmodifiableList(Ljava/util/List;)Ljava/util/List;
    move-result-object v6
    invoke-interface {v5}, Ljava/util/List;->size()I
    move-result v3
    sub-int v3, v2, v3
    invoke-static {v3}, Lapp/local/youtube/adblock/LocalDiagnostics;->shortsEntriesFiltered(I)V
    :try_end
    .catch Ljava/lang/Exception; {:try_start .. :try_end} :failed
    const/4 v1, 0x0
    aput-object v5, v0, v1
    const/4 v1, 0x1
    aput-object v6, v0, v1
    :done
    return-object v0
    :failed
    move-exception v1
    return-object v0
.end method

.method public static filterSeed(Lasrd;)[Ljava/util/List;
    .registers 3
    iget-object v0, p0, Lasrd;->a:Ljava/util/List;
    iget-object v1, p0, Lasrd;->b:Ljava/util/List;
    invoke-static {v0, v1}, Lapp/local/youtube/adblock/ShortsAds;->filterPair(Ljava/util/List;Ljava/util/List;)[Ljava/util/List;
    move-result-object v0
    return-object v0
.end method
