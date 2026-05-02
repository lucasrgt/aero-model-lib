# Roadmap de performance - AeroModelLib

Checklist para investigação de gargalos. Este arquivo separa claramente o que
foi entregue na lib, o que depende de adoção nos mods consumidores, o que só
deve avançar com benchmark/JFR e o que está bloqueado por arquitetura.

Última limpeza de status: 2026-05-02.

## Legenda

- [x] **Feito:** código entregue, validado localmente quando aplicável.
- [ ] **Não feito:** aberto, deferido, bloqueado, ou dependente de benchmark.
- **INFRA:** a lib tem o mecanismo, mas o mod consumidor ainda precisa adotar.
- **OPT-IN:** existe, mas fica desligado por padrão ou controlado por flag.

## Validação Atual

- [x] `modloader/tests/run.ps1`: 222 testes passando.
- [x] `stationapi`: `compileJava remapJar` passando.
- [x] `stationapi/test`: `compileJava` passando.
- [x] Spike logger em torre densa: Cell Pages/BPDL não recompilam durante os
  spikes capturados (`cellRebuilds=0`, display lists estáveis); o maior spike
  atribuído foi GC/heap comprometido baixo (`heap` ~240 MB apesar de `Xmx`
  alto). Tasks de benchmark agora usam `-Xms2G -Xmx4G`.
- [x] Nova rodada com logger refinado inocentou as otimizações novas nos
  spikes ritmados restantes: frames de `196-272 ms` tiveram
  `renderWorldMs <= 0.2`, `aeroPrepMs <= 0.2`, `renderEntitiesMs <= 0.8`,
  `worldFlushMs <= 1.2`, `cellRebuilds=0` e `GC=0`. O problema remanescente
  deve ser tratado como legado/externo ao renderer otimizado: stall de
  chunk/terrain/driver/swap buffers disparado pela cena densa da lib.
- [x] Rodada seguinte com contadores acumulados confirmou o gargalo de
  terreno: um spike de `284 ms` teve `renderChunksMs=111.7`,
  `renderChunksMaxMs=111.5`, `displayUpdateMs=159.5`, `renderEntitiesMs=0.6`,
  `worldFlushMs=0.8`, `cellRebuilds=0` e `GC=0`. O MEGA benchmark agora usa
  terreno esparso por padrão; o modo legado de lajes 16x16 continua disponível
  com `-Daero.mega.solidTerrain=true`.
- [x] JFR posterior apontou pressão de alocação em
  `TextureManager.getTextureId(String)`; a lib agora expõe
  `Aero_TextureBinder.bind(path)` e o showcase denso usa esse caminho cacheado
  em vez de `BlockEntityRenderer.bindTexture(String)` por BE/frame.
- [x] JFR em MEGA esparso mostrou custo residual menor em
  `Aero_BECellIndex.track` / `IdentityHashMap.get`; a base
  `Aero_RenderDistanceBlockEntity` agora faz tracking completo no tick e só
  reanexa durante `distanceFrom(...)` se mundo/posição mudarem.
- [x] JFR com side effects e spike logger desligados revelou o culpado antigo
  dos spikes/degradação de FPS: `PalettedContainerCacheMixin` estava aplicado
  mesmo com a cache default-off. O método quente `PalettedContainer.get(int)`
  ainda alocava `CallbackInfoReturnable` por chamada, dominando heap/chunk
  render. Corrigido: o plugin agora só aplica o mixin quando
  `-Daero.palettedcache=true` ou `-Daero.palettedcache.chunkScope=true`.
- [x] Os spikes ritmados de 30-40 ms em MEGA esparso apareceram
  majoritariamente como `worldSaveMs=20-26` dentro do tick; a tarefa
  `runClientMegaSpikeClean` isola render desligando JFR, side effects do MEGA
  e saves não-forçados de benchmark.
- [x] Lazy bake do chunk-bake registry promovido a pre-warm idempotente em
  `Aero_RenderDistance.beginRenderFrame()`. Primeiro chunk-compile que
  encontra um bloco registrado não paga mais `float[4][][]` + `float[15]`
  por triângulo dentro do `BlockRenderManager.render` path.
- [x] `Aero_MeshRenderer.runIkChains` agora reutiliza scratch arrays
  (`target`, `boneIdx`, `pivots`) — cadeias de mesmo tamanho zero-alocam em
  steady state. Validado com `runClient` limpo: 200+ FPS no MEGA densa,
  800+ olhando para fora da torre.
- [ ] Rodar benchmark visual/stress com muitos BEs reais.
- [ ] Rodar JFR antes/depois para confirmar ganho real em produção.
- [ ] Conferir adoção em mods consumidores reais.
- [ ] Conectar Beta Energistics à AeroModelLib antes de tentar migrar BEs. Neste
  checkout não há uso visível de `Aero_Mesh*`,
  `Aero_RenderDistanceBlockEntity` ou `Aero_CellPageRenderableBE`.

## Restrições Fixas Da Plataforma

- [x] Alvo: Minecraft Beta 1.7.3 + StationAPI/Babric.
- [x] Render: LWJGL 2 / OpenGL 1.x fixed-function.
- [x] Sem shaders programáveis, instancing moderno, compute, MRT ou indirect
  draw.
- [x] Canais reais de submissão: Tessellator vanilla e display lists.
- [x] Técnica nova precisa de fallback conservador e opt-out/opt-in por flag.

## Próximos Gargalos A Confirmar

- [ ] Adoção incompleta de `Aero_CellPageRenderableBE` nos mods reais.
- [ ] Iteração vanilla por todos os BEs antes do skip por `distanceFrom`.
  - [x] Mitigação parcial: a chamada ainda existe, mas não força full
    `Aero_BECellIndex.track(...)` por BE/frame quando o BE não se moveu.
- [ ] Triângulos desnecessários em OBJ grande sem
  `-Daero.obj.cullhidden=true`.
- [ ] Chunk meshing / `PalettedContainer.get` em entrada de mundo ou chunk novo,
  agora com modo chunk-scoped para A/B; default-off não aplica mixin nenhum.
- [ ] Falta de LODs reais nos assets dos consumidores.
- [ ] Avaliar modo high-memory se CPU/driver continuarem no topo.
- [ ] Validar flags de fragmentação de Cell Pages em cena real:
  `perInstanceLight`, `lightBuckets`, `stableMembership`, `maxCachedPages`.
- [ ] Validar em jogo se os lag spikes ritmados perto da torre sumiram com
  heap inicial maior (`-Xms2G`) e, se persistirem, capturar JFR para separar
  GC/JIT/chunk rebuild/driver.
  - [x] Para isolar renderer, usar `runClientMegaSpikeClean` ou
    `-Daero.benchmark.skipNonForcedSaves=true` em benchmark; não usar isso em
    gameplay normal.
- [ ] Confirmar com os novos campos do spike logger se o stall legado aparece
  em `compileChunksMs`, `renderChunksMs` ou em nenhum trecho Java medido
  (provável bloqueio driver/GPU/swap).
- [ ] Revalidar MEGA em mundo novo ou chunks novos após a troca para terreno
  esparso; mundos antigos ainda contêm as lajes sólidas já salvas.
- [ ] Validar MEGA com side effects religados
  (`-Daero.mega.sideEffects=true`) quando o foco for eventos de keyframe,
  som e partículas, não FPS de renderer.
- [ ] Consumers StationAPI devem trocar renderers de modelo densos para
  `Aero_TextureBinder.bind(path)` ou caminhos internos da lib
  (`Aero_AnimatedBatcher`, Cell Pages), evitando resolver textura por string a
  cada BE.

---

## Grupo A - Render Queue, Culling E Estado GL

- [x] **A1. Chunk visibility / PVS por `inFrustum` - CONCLUÍDO, BENCH PENDENTE**
  - [x] `Aero_ChunkVisibility` tira snapshot de `WorldRenderer.chunks[]`.
  - [x] Usa `inFrustum` e hardware occlusion query quando disponível.
  - [x] `Aero_RenderDistance.blockEntityDistanceFrom(...)` retorna `Infinity`
    para BEs em chunk invisível.
  - [x] Toggle: `-Daero.chunkvisibility=false`.
  - [ ] Medir em JFR queda em `Aero_Frustum6Plane` / dispatcher BE.

- [ ] **A2. Brightness buckets configuráveis/interpolados - ABERTO P2**
  - [x] Hoje existem 4 buckets via `Aero_MeshModel.BRIGHTNESS_FACTORS`.
  - [ ] Avaliar 8 buckets se houver banding visual real.
  - [ ] Avaliar 4 buckets + interpolação de cor se memória de lists virar
    gargalo.

- [x] **A3. Animated batcher sort por textura - CONCLUÍDO**
  - [x] `Aero_AnimatedBatcher` ordena batches por `texturePath`.
  - [x] Deduplica binds adjacentes com `lastBoundPath`.
  - [x] Toggle: `-Daero.batcher.sort=false`.
  - [ ] Medir `glBindTexture`/estado GL em trace/JFR.

- [x] **A4. Animation curve LUT bake - CONCLUÍDO, OPT-IN**
  - [x] `Aero_AnimationLUTConfig` e LUT por canal.
  - [x] Flag: `-Daero.anim.lut=true`.
  - [x] Flag: `-Daero.anim.lut.samples=N` (default `64`).
  - [ ] Medir custo de easing / binary search / slerp em stress real.

- [x] **A5. Composite-key sort do animated batcher - CONCLUÍDO**
- [x] Batch key inclui modelo, textura, tint/alpha, `alphaClip`, blend,
    `depthTest` e `cullFaces`.
  - [x] Flush ordena por chave composta para reduzir troca de estado GL.
  - [x] Lookup por chave reutilizável evita alocação de `BatchKey`
    temporária por BE/frame.
  - [x] Toggle: `-Daero.batcher.sort=false`.
  - [ ] Medir binds/estado na cena do consumidor.

- [x] **A6. Índice global para não iterar BE vanilla - INFRA PARCIAL**
  - [x] `Aero_BECellIndex` agrupa BEs opt-in por mundo+célula.
  - [x] Dirty flags de state/orientation/canCellPage.
  - [x] C5 já pula renderer individual para BEs cell-managed em LOD estático.
  - [ ] Vanilla ainda itera a lista de BEs e chama `distanceFrom(...)`.
  - [ ] Só atacar mixin/dispatcher próprio se JFR mostrar esse custo no topo.

---

## Grupo B - Mesh, OBJ E Chunk Meshing

- [ ] **B1. OBJ vertex welding - DEFERIDO**
  - [x] Diagnóstico: welding sozinho não reduz upload porque a malha é
    triângulo flatten em `float[]`.
  - [ ] Só vale como pré-requisito se B2 precisar detectar faces internas mais
    complexas.

- [x] **B2. OBJ hidden face removal - CONCLUÍDO, OPT-IN**
  - [x] `Aero_ObjLoader` remove pares de triângulos coincidentes e opostos no
    mesmo grupo OBJ.
  - [x] Não cruza grupos/named bones, preservando partes móveis.
  - [x] Flag: `-Daero.obj.cullhidden=true`.
  - [x] Flag: `-Daero.obj.cullhidden.grid=N` (default `4096`).
  - [ ] Medir redução de triângulos em OBJs reais.

- [ ] **B3. Mipmap em texturas de modelo - DEFERIDO**
  - [x] Diagnóstico: modellib usa `TextureManager` vanilla por path.
  - [ ] Mipmap exigiria mixin global ou loader paralelo, com risco alto em
    pixel-art.

- [x] **B4. Alpha clipping em `Aero_RenderOptions` - CONCLUÍDO**
  - [x] `alphaClip(threshold)`.
  - [x] `Aero_RenderOptions.alphaClipped(...)`.
  - [x] Estado GL restaurado nos dois runtimes.

- [x] **B5. Small-object culling - CONCLUÍDO**
  - [x] Integrado em `shouldRenderRelative`.
  - [x] Integrado em `lodRelative`.
  - [x] Integrado em `blockEntityDistanceFrom`.
  - [x] Flag: `-Daero.smallobj=false`.
  - [x] Flag: `-Daero.smallobj.px=N`.

- [x] **B6. Skeletal LOD intermediário - CONCLUÍDO, OPT-IN**
  - [x] Render animado pode limitar profundidade de cadeia de pose em distância.
  - [x] Flag: `-Daero.skeletalLod=true`.
  - [x] Flag: `-Daero.skeletalLod.distance=N` (default `48`).
  - [x] Flag: `-Daero.skeletalLod.depth=N` (default `1`).
  - [x] Fallback automático para procedural pose, IK e morph ativo.

- [ ] **B7. Mesh quantization - BLOQUEADO / DEFERIDO**
  - [x] Diagnóstico: a malha pública ainda é `float[][][]` flatten.
  - [ ] Precisa novo formato/tooling de asset ou backend compacto.
  - [ ] Não implementar como cópia extra em runtime, porque aumentaria heap sem
    reduzir CPU/driver.

- [ ] **B8. Async chunk meshing - ABERTO P1 GRANDE**
  - [ ] Ataca entrada de mundo/chunk novo, não render steady state.
  - [ ] Precisa desenho seguro para contrato vanilla/StationAPI.
  - [ ] Flag proposta: `-Daero.chunkmesh.async=true` default off.

- [x] **B9. Cache chunk-scope no caller de `PalettedContainer.get` - CONCLUÍDO, OPT-IN**
  - [x] `ChunkBuilderPalettedCacheScopeMixin` abre escopo em
    `ChunkBuilder.rebuild()` somente quando chunk-scope está opt-in.
  - [x] `PalettedContainerCacheMixin` é aplicado somente no modo global antigo
    ou no escopo opt-in; default-off não injeta no método quente.
  - [x] Flag nova: `-Daero.palettedcache.chunkScope=true`.
  - [x] Flag A/B antiga: `-Daero.palettedcache=true`.
  - [ ] Benchmark de entrada de mundo/chunk novo antes de ligar por padrão.

- [x] **B10. Motion-based animation simplification - CONCLUÍDO**
  - [x] `Aero_AnimationTickLOD.tickStrideWithMotion(...)`.
  - [x] Overloads em `Aero_RenderDistanceBlockEntity`.
  - [x] Overloads em `Aero_RenderDistanceTileEntity`.
  - [ ] Consumidores móveis precisam chamar
    `shouldTickAnimation(velocityBlocksPerTick)`.

- [ ] **B11. Billboard distante - BLOQUEADO / DEFERIDO**
  - [ ] Ideia: trocar at-rest distante por sprite/silhueta.
  - [ ] Precisa investigar FBO/offscreen em Beta/LWJGL 2 ou bake offline.
  - [ ] Não implementar runtime sem pipeline seguro de estado GL.

- [x] **B12. LODs reais de modelo / decimation - INFRA CONCLUÍDA**
  - [x] `Aero_ModelSpec.mesh(...).meshLod(...)`.
  - [x] Aceita OBJs ou `Aero_MeshModel`s alternativos por distância.
  - [x] Render estático/at-rest escolhe mesh por distância.
  - [x] Render animado continua no mesh base para preservar named bones.
  - [ ] Consumidores precisam criar assets `lod1/lod2` ou pipeline offline.

---

## Grupo C - BE Cell Pages / Escala Massiva Em Tela

- [x] **C0. Animation render budget - CONCLUÍDO**
  - [x] `Aero_AnimationRenderBudget` rebaixa overflow `ANIMATED -> STATIC`.
  - [x] Mantém objeto visível.
  - [x] Flag: `-Daero.animBudget=false`.
  - [x] Flag: `-Daero.maxAnimatedBE=N` (default `128`, `-1` ilimitado).

- [x] **C1. Prioridade de animação por importância - CONCLUÍDO**
  - [x] Prioridade por tamanho projetado/distância.
  - [x] Reserva crítica para objetos próximos/grandes.
  - [x] Histerese para evitar flicker animated/static.
  - [x] Flags: `criticalPx`, `midPx`, `lowPx`, `nearBlocks`,
    `criticalExtra`, `hysteresisFrames`, `hysteresisExtra`.
  - [x] Caminho StationAPI usa chave primitiva `long` para histerese,
    evitando boxing/alocação por BE/frame perto de torres densas.

- [x] **C2. BECellIndex por célula - INFRA CONCLUÍDA / ADOÇÃO PARCIAL**
  - [x] `Aero_CellRenderableBE`.
  - [x] `Aero_BECellIndex` por mundo+célula.
  - [x] Adoção automática para classes que herdam
    `Aero_RenderDistanceBlockEntity`.
  - [x] `Aero_RenderDistanceBlockEntity.distanceFrom(...)` evita revalidar
    estado/célula por frame quando mundo/posição não mudaram; o full track
    continua no tick/lifecycle.
  - [ ] BEs que não herdam a base precisam chamar track/untrack ou ganhar hook.
  - [ ] Migrar consumidores reais.

- [x] **C3. Cell at-rest pages - INFRA CONCLUÍDA / ADOÇÃO PARCIAL**
  - [x] `Aero_BECellRenderer.queueAtRest(...)`.
  - [x] `Aero_CellPageRenderableBE`.
  - [x] Flush compila display lists por célula com transforms locais.
  - [x] Flags: `-Daero.becell.pages=false`,
    `-Daero.becell.minInstances=N`, `-Daero.becell.pageTtlFrames=N`,
    `-Daero.becell.rebuildsPerFrame=N`.
  - [ ] Consumidores precisam implementar `Aero_CellPageRenderableBE` nos BEs
    elegíveis.

- [x] **C3.1. Fragmentação/churn de Cell Pages - CONCLUÍDO, OPT-IN**
  - [x] PageKey guarda hash pré-computado para reduzir custo de HashMap.
  - [x] Lookup por chave reutilizável evita alocação de `PageKey`
    temporária por BE/frame.
  - [x] Flag: `-Daero.becell.perInstanceLight=true` remove brightness exato
    da PageKey e grava cor por instância dentro da display list.
  - [x] Flag: `-Daero.becell.lightBuckets=N` quantiza brightness da PageKey
    quando per-instance light está desligado (`0` = exato/default).
  - [x] Flag: `-Daero.becell.stableMembership=true` ordena membros da página
    antes de hashear/compilar, evitando rebuild por ordem instável.
  - [x] Flag: `-Daero.becell.maxCachedPages=N` limita páginas cacheadas com
    evicção por menor `lastUsedFrame` (`-1` = sem limite/default normal).
  - [x] Métricas: `compiledCachedPages`, `expiredCachedPages`,
    `evictedCachedPages`, `cachedPageCount`, `directFallbacksThisFrame`.
  - [ ] Rodar A/B com muitos BEs reais para escolher defaults por mod/cena.

- [x] **C4. Central cell flush - CONCLUÍDO**
  - [x] `Aero_BECellRenderer.flush(...)` no fim de
    `WorldRenderer.renderEntities`.
  - [x] Flush junto ao `Aero_AnimatedBatcher`.
  - [x] Failsafe no começo do próximo frame.
  - [x] Profiler: `aero.becell.flush`, `compile`, `call`, `direct`.

- [x] **C5. Skip do renderer individual para BEs cell-managed - CONCLUÍDO**
  - [x] `Aero_RenderDistanceBlockEntity.distanceFrom(...)` resolve LOD.
  - [x] BE paginável em STATIC enfileira cell page.
  - [x] Retorna `Infinity` para vanilla e evita BER individual.
  - [x] Toggle: `-Daero.becell.skipIndividual=false`.
  - [ ] Ainda resta custo de iteração vanilla + chamada `distanceFrom(...)`;
    mitigado parcialmente pelo tracking leve em render.

---

## Grupo D - Bone Pages / Animação Rígida

- [x] **D1. Bone-page display lists - CONCLUÍDO**
  - [x] BPDL para grupos rígidos/named groups.
  - [x] Geometria elegível vira display list por bone/bucket.
  - [x] Render animado aplica matriz do bone e chama `glCallList`.
  - [x] StationAPI mantém clips/grupos com `uv_offset` ou `uv_scale` no
    caminho Tessellator preciso. Isso preserva runas/conveyors e evita a
    matriz de textura em stacks GL 1.1 sensíveis a estado.
  - [x] Flag: `-Daero.bonepages=false`.
  - [x] Flag: `-Daero.bonepages.minTris=N` (default `24`).
  - [x] Profiler: `aero.bonepages.compile`, `aero.bonepages.call`.

---

## Grupo E - High-Memory Performance Mode

- [x] **E1. Preset agressivo de cache RAM/driver - INFRA CONCLUÍDA, BENCH PENDENTE**
  - [x] Preset `-Daero.perf.memory=high`.
  - [x] TTL de Cell Pages sobe de `600` para `1800` se a flag específica não
    for passada.
  - [x] Rebuild budget de Cell Pages sobe de `8` para `16` se a flag
    específica não for passada.
  - [x] `Aero_Prewarm.enqueueModel(...)` preaquece at-rest lists e bone pages
    em frames controlados.
  - [x] LUT de animação liga por padrão dentro do preset high-memory.
  - [x] Cache de OBJ/JSON runtime fica sem limite dentro do preset
    (`aero.modellib.cache.maxEntries=-1` efetivo).
  - [ ] Usar LODs reais em assets grandes.
  - [x] `Aero_DisplayListBudget` limita display lists vivas em StationAPI.
  - [x] Métricas: `liveLists`, `peakLiveLists`, `totalAllocatedLists`,
    `deniedAllocations`, `failedAllocations`.
  - [x] Flags: `-Daero.prewarm=true`, `-Daero.prewarm.perFrame=N`,
    `-Daero.prewarm.maxMsPerFrame=N`,
    `-Daero.becell.pageTtlFrames=N`,
    `-Daero.becell.rebuildsPerFrame=N`,
    `-Daero.displayLists.maxLive=N`,
    `-Daero.displayLists.warnLive=N`.
  - [ ] Prewarm de Cell Pages completas ainda depende de captura segura de
    membership/estado da célula; por enquanto o prewarm cobre modelo at-rest e
    bone pages.
  - [ ] Validar risco de driver/VRAM em máquinas fracas.

---

## Próxima Onda Recomendada

- [ ] Rodar stress/JFR com muitos BEs reais.
- [ ] Comparar baseline atual.
- [ ] Comparar `-Daero.becell.skipIndividual=false`.
- [ ] Comparar `-Daero.becell.pages=false`.
- [ ] Comparar `-Daero.bonepages=false`.
- [ ] Comparar `-Daero.maxAnimatedBE=-1`.
  - Use `./gradlew runClientBenchmark -Pbench=N -PaeroJvmArgs="FLAGS"`
    no `stationapi/test` para passar flags A/B sem editar Gradle.
- [ ] Confirmar se topo do JFR é renderer individual, `distanceFrom`,
  `Aero_AnimatedBatcher`, estado GL, OBJ tri count ou chunk meshing.
- [ ] Conectar mods reais que usam muitos BEs à AeroModelLib.
- [ ] Implementar `Aero_CellPageRenderableBE` onde renderer puder virar cell
  page.
- [ ] Criar assets `lod1/lod2` reais e declarar via
  `Aero_ModelSpec.meshLod(...)`.
- [ ] Rodar A/B de `-Daero.obj.cullhidden=true` em OBJs grandes.
- [ ] Rodar A/B de `-Daero.palettedcache.chunkScope=true` em entrada de mundo.
- [ ] Rodar A/B de `-Daero.perf.memory=high` e conferir stutter/VRAM/driver.
- [ ] Rodar A/B de `-Daero.becell.perInstanceLight=true` se
  `directFallbacksThisFrame`/páginas pequenas indicarem fragmentação por luz.
- [ ] Rodar A/B de `-Daero.becell.stableMembership=true` se
  `pageRebuildsThisFrame` ficar alto sem mudança visual real.
- [ ] Só considerar A6 dispatcher/cell iteration invasivo se iteração vanilla
  por BE continuar cara depois do skip individual.

## Bloqueados / Não Fazer Ainda

- [ ] **B7 mesh quantization:** bloqueado pelo contrato interno `float[][][]`;
  precisa novo formato/tooling para reduzir heap de verdade.
- [ ] **B11 billboard distante:** bloqueado por falta de pipeline FBO/offline
  bake seguro para Beta/LWJGL 2.
- [ ] **Migração Beta Energistics:** bloqueada até o mod consumir AeroModelLib
  nos renderers relevantes.
