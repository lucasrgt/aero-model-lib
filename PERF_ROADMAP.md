# Roadmap de performance — modellib

Checklist de trabalho para fechar dois grupos de pendências:

1. **A→A** — itens hoje **parcialmente cobertos** que vamos promover a cobertura completa.
2. **B→A** — itens hoje **viáveis mas não implementados** que vamos implementar.

Plataforma alvo (constraint duro, recapitulação):
- Beta 1.7.3 + StationAPI/Babric, runtime alvo é o stationapi (modloader herda via `core/`)
- LWJGL 2 / GL 1.x fixed-function (sem shaders programáveis, sem MRT, sem instancing nativo, sem compute, sem GL 4.x indirect)
- Tessellator vanilla + display lists são os dois canais reais de submissão
- Chunk vertex buffer do vanilla é acessível via mixin (já provado em `BlockRenderManagerChunkBakeMixin`)

Critérios gerais de pronto (aplicam-se a todo item):
- [ ] Toggle `-Daero.<feat>=false` para opt-out (mantém compat com mods que não querem)
- [ ] Linha em `CHANGELOG.md` na próxima release com descrição + repro
- [ ] Verificado no `runClientBenchmark -Pbench=60 -Daero.stresstest=true` (sem regressão acima de ±3 FPS na cena base)
- [ ] Sem regressão nos 210 unit tests (`bash scripts/test_unit.sh` no consumer mais próximo)

Convenções de prioridade:
- **P0** — quick win com ROI claro, < 1 dia
- **P1** — ROI claro mas exige design ou mixin novo
- **P2** — defer até consumer reclamar / cena justificar

---

## Grupo A→A — promover parciais a completos

### A1. Visibility cells / PVS via `inFrustum` por chunk — **P1**
**Hoje:** modellib reusa o `FrustumData` que vanilla calcula uma vez por frame; o teste é per-BE.
**Falta:** capturar a flag `inFrustum` que `WorldRenderer.cullChunks` grava em cada `WorldRenderer` (chunk slice 16³) e usar como **broad-phase** antes do per-BE 6-plane. BE em chunk não-visível pula o teste de plano.
**Arquivos prováveis:**
- `stationapi/src/main/java/aero/modellib/mixin/WorldRendererChunksAccessor.java` (já existe, ampliar)
- novo mixin TAIL em `WorldRenderer.cullChunks` para snapshot do mapa `chunkX,chunkY,chunkZ → inFrustum`
- `Aero_Frustum6Plane` — short-circuit se a cell do BE está marcada não-visível
**Dep:** nenhuma.
**Critério de pronto:**
- [ ] Snapshot estável do mapa de chunks visíveis por frame
- [ ] Short-circuit no `blockEntityDistanceFrom` antes do 6-plane
- [ ] JFR mostra queda em `Aero_Frustum6Plane.intersects` proporcional aos chunks descartados

### A2. Brightness baking em mais buckets / interpolado — **P2**
**Hoje:** `renderModelAtRest` cacheia 4 display lists por modelo, uma por bucket de brightness.
**Falta:** transição visível em borda de bucket (4 níveis é grosseiro). Opções: 8 buckets (dobra VRAM de display lists, ~8 KB por modelo), ou 4 buckets + `glColor4f` interpolado entre dois mais próximos no draw.
**Arquivos prováveis:**
- `stationapi/src/main/java/aero/modellib/Aero_MeshRenderer.java`
**Dep:** nenhuma.
**Critério de pronto:**
- [ ] Bucket count configurável `-Daero.brightness.buckets=N` (default 4 mantém compat)
- [ ] Interpolação opcional `-Daero.brightness.lerp=true`
- [ ] Sem regressão de FPS visível no benchmark base

### A3. Animated batcher cross-model (sort por textura) — **CONCLUÍDO**
**Entregue:** sort de `ACTIVE_BATCHES` por `texturePath` antes do drain (skip quando só 1 batch). `bindBatchTexture` ganhou dedup via `lastBoundPath` — adjacent same-texture batches custam zero `glBindTexture`. `lastBoundPath` reseta no início de `flush()` porque vanilla muda bind entre frames.
**Critério de pronto:**
- [x] Drain ordenado por `texturePath` ascendente (nulls-first comparator)
- [x] `bindBatchTexture` skip quando path == lastBoundPath
- [x] Toggle `-Daero.batcher.sort=false`
- [ ] Métrica de texture binds (deferred — Aero_Profiler não tracka glBindTexture; provar com JFR/RenderDoc quando alguém investigar)

### A4. Animation curve LUT bake — **CONCLUÍDO**
**Entregue:** `Aero_AnimationLUTConfig` (env vars `aero.anim.lut=true` default-OFF + `aero.anim.lut.samples=N` default 64). `ChannelTrack` ganhou `lut[][]`, `lutTimeMin`, `lutTimeRange`. Construtor chama `bakeLut(SAMPLES)` quando ENABLED — chama `sampleRaw` em N tempos uniformes em `[times[0], times[n-1]]` e snapshota o output final pós-easing/slerp/decode. `sampleInto` virou dispatcher: LUT presente → `sampleLut` O(1) com lerp adjacente; senão fall-through para `sampleRaw` (renomeado do body original).
**Critério de pronto:**
- [x] LUT opt-in via `-Daero.anim.lut=true` (default off)
- [x] Tolerável: 210 unit tests passam **tanto** com LUT off quanto com LUT on (parity verificada)
- [x] Lookup runtime: 1 mul + 1 cast + 1 index + 3 lerps (vs. binary search + easing fn + slerp + atan2/asin/atan2)
- [ ] JFR confirmando colapso em stress (deferred — quando alguém investigar com benchmark)

### A5. Render queue: ordenação interna do pass animated — **P1**
**Hoje:** ordem de submissão é a ordem de inserção no batcher.
**Falta:** dentro de um mesmo `textureId` (após A3), ordenar por **estado GL completo** (alpha/blend/depth) para minimizar `glEnable/glDisable`. Composite-key sort.
**Arquivos prováveis:** mesmos de A3.
**Dep:** A3.
**Critério de pronto:**
- [ ] Composite-key sort ativo
- [ ] Métrica de `glEnable/glDisable` por frame (Aero_Profiler) cai
- [ ] Toggle compartilhado com A3

### A6. Spatial partitioning fino: BEs indexados por chunk — **P2**
**Hoje:** chunks vanilla são particionamento implícito; cada BE ainda passa per-frame pelos cull layers.
**Falta:** índice `chunkKey → List<BE>` mantido pelo modellib (subscribe a load/unload de chunk via mixin). Junto com A1, um chunk fora do frustum descarta a lista inteira sem iterar.
**Arquivos prováveis:**
- novo `Aero_BlockEntityIndex.java` em stationapi/
- mixin em `World.setBlockEntity` / `Chunk.removeBlockEntity` para manter índice
**Dep:** A1 (sem `inFrustum` por chunk, o índice não acelera nada).
**Critério de pronto:**
- [ ] Índice consistente com `World.blockEntities` em todo frame
- [ ] Em cena com 500+ BEs, JFR mostra `BlockEntityRenderDispatcher.render` não iterando lista vanilla
- [ ] Toggle `-Daero.beindex=false`

---

## Grupo B→A — implementar viáveis

### B1. OBJ load: vertex welding — **DEFERIDO (mesclar com B2)**
**Premise check falhou.** O parser flatten triângulos como `float[15]` totalmente expandidos e o renderer usa `Tessellator → glDrawArrays` (sem index buffer), então welding na list `verts` durante parse **não reduz upload nem afeta display list cache**. O único valor real do B1 é ser pré-requisito de B2 (hidden face removal, que precisa de canonical vertex IDs pra detectar adjacência por aresta). Item mantido como sub-task interno do B2 quando este for atacado.

### B2. OBJ load: hidden face removal — **P1**
**Falta:** faces totalmente internas (entre dois sólidos do mesmo modelo, ambos lados oclusos) nunca aparecem; ainda são submetidas. Detect via teste de adjacência por aresta no load.
**Arquivos:** mesmo de B1.
**Dep:** B1 (welding precisa rodar antes para detectar adjacência).
**Critério de pronto:**
- [ ] Opt-in `-Daero.obj.cullhidden=true` (default off — alguns modelos esperam ver dentro)
- [ ] Teste mostra redução de faces > 0 em mega-model
- [ ] Sem visual diff em modelos onde câmera não entra

### B3. Mipmap em texturas de modelo — **DEFERIDO (P1, escopo subestimado)**
**Premise check falhou.** Modellib não carrega texturas próprias — só faz `mc.textureManager.bindTexture(path)` via vanilla TextureManager (`Aero_AnimatedBatcher.bindTexturePath`). Pra mipmap precisaria mixin no TextureManager (afeta TODAS texturas vanilla, quebra estética pixelada) ou criar um `Aero_TextureLoader` paralelo via LWJGL direto. Ambos os caminhos são P1+, não P0. Re-listar quando atacarmos a infraestrutura de texturas.

### B4. Alpha clipping mode em `Aero_RenderOptions` — **CONCLUÍDO**
**Entregue:** API `RenderOptions.alphaClip(threshold)` + factory `Aero_RenderOptions.alphaClipped(threshold)`. Default `0f` = disabled (preserva comportamento). `>0f` ativa `glAlphaFunc(GL_GREATER, threshold)` + `glEnable(GL_ALPHA_TEST)`. Stack com `blend(...)` quando ambos são setados (clip antes do blend, útil pra foliage com bordas suaves). `MESH_ATTRIB_BITS` ganhou `GL_COLOR_BUFFER_BIT` pra restaurar `glAlphaFunc` no `glPopAttrib` e não vazar threshold pra renderização vanilla seguinte.
**Critério de pronto:**
- [x] API `RenderOptions.alphaClip(threshold)` distinta de `alpha(...)` e `blend(...)`
- [x] Implementado em ambos os runtimes (stationapi + modloader)
- [x] 210 unit tests passam (sem regressão em `Aero_RenderOptions` shared)
- [ ] Showcase usando clip (deferred — quando aparecer caso real, hoje ninguém precisa)

### B5. Small-object culling — **CONCLUÍDO**
**Entregue:** `Aero_SmallObjectCull` (core/) com matemática `2 * focal / threshold` cached por display height. Per-BE: 1 mul + 1 compare. Wired em `Aero_RenderDistance.shouldRenderRelative`, `lodRelative`, `blockEntityDistanceFrom` — todos após o distance check, antes do cone. Math-defined (sem falso-positivo, ao contrário das heurísticas geométricas).
**Critério de pronto:**
- [x] Check inserido após distance, antes do cone
- [x] Toggle `-Daero.smallobj=false` + threshold `-Daero.smallobj.px=N`
- [x] 210 unit tests passam
- [ ] JFR queda em cenas com horizonte denso (deferred — confirmar quando alguém criar showcase com 100+ small BEs)

### B6. Skeletal LOD intermediário — **P2**
**Falta:** hoje LOD é binário (anima vs. at-rest). Tier intermediário "anima só ossos com `depth ≤ N` na hierarquia, resto rest" ≈ 70% custo por 30% detalhe.
**Arquivos:**
- `core/aero/modellib/Aero_AnimationTickLOD.java` (novo tier)
- `Aero_AnimationPoseResolver` (skip ossos abaixo do depth threshold)
**Dep:** nenhuma.
**Critério de pronto:**
- [ ] Tier `RIG_SHALLOW` adicionado entre `RIG_FULL` e `STATIC`
- [ ] Distância de switch configurável
- [ ] Visual diff aceitável em mega-model com 5+ níveis hierárquicos

### B7. Mesh data layout: quantização opcional — **P2**
**Falta:** `Aero_MeshModel` mantém `float[]` cru pra positions/uvs/normals. Quantização (short com escala) corta RAM 50%. Display list não vê — é só economia de heap.
**Arquivos:** `core/aero/modellib/Aero_MeshModel.java`, `Aero_ObjLoader.java`.
**Dep:** B1 (welding deve rodar antes; quantização só na representação serializada).
**Critério de pronto:**
- [ ] Opt-in `-Daero.mesh.quantize=true`
- [ ] Erro de posição < 1/4096 (16-bit fixed point com bbox normalization)
- [ ] Teste unit: round-trip quantize/dequantize preserva geometria visualmente

### B8. Async chunk meshing (caminho 4.0) — **P1 (escopo grande)**
**Falta:** vanilla chunk meshing é single-threaded síncrono. JFR confirma 47% do tempo de world-entry em `PalettedContainer.get` + HashMap traversals do meshing. **Maior single win pendente.**
**Riscos:** muda contrato vanilla; exige cooperação StationAPI; cache hazards (read enquanto chunk sendo modificado).
**Arquivos:**
- mixin novo em `WorldRenderer.compileChunks` (ou equivalente StationAPI)
- pool de threads pequeno (2-4) com fila de chunks dirty
- merge result no main thread no end-of-frame
**Dep:** validar com calmilamsy / StationAPI primeiro (ver `grumpy-calmilamsy` skill).
**Critério de pronto:**
- [ ] Pool desligado por default; `-Daero.chunkmesh.async=true` opt-in
- [ ] World-entry time cai mensurável (medir com `Aero_Profiler` em mod-side)
- [ ] Sem race em chunks atravessando boundary

### B9. Cache no caller (PalettedContainer chunk-scope) — **P1**
**Falta:** v3.0 adicionou então reverteu cache @Inject HEAD/RETURN no `PalettedContainer.get` por overhead × volume. Solução correta: cache em `WorldRenderer.compileChunks` HEAD — escope o cache à vida de um build de chunk, paga overhead 1× por chunk em vez de 1× por get.
**Arquivos:** novo `WorldRendererChunkBuildCacheMixin`.
**Dep:** B8 (se chunk meshing for async, cache vai por thread).
**Critério de pronto:**
- [ ] Cache ativo só durante `compileChunks`
- [ ] FPS steady-state sem regressão (≤ ±2 FPS vs. baseline 3.0)
- [ ] World-entry time cai em chunks novos

### B10. Motion-based animation simplification — **P2**
**Falta:** BE/entity se movendo rápido não permite o olho discriminar pose precisa. Skip de tick adicional quando velocidade > threshold.
**Arquivos:** `core/aero/modellib/Aero_AnimationTickLOD.java`.
**Dep:** nenhuma.
**Critério de pronto:**
- [ ] Threshold de velocidade configurável
- [ ] Comportamento default conservador (só skip em > 8 m/s, ~Speed II)
- [ ] Teste manual: minecart em rail mostra animação ainda crível

### B11. Billboard distante para showcase — **P2**
**Falta:** além do swap animated→at-rest, swap final at-rest→sprite quando distância > 2× LOD máximo. Sprite gerado uma vez via `glReadPixels` da display list em FBO de side-render no load (se possível em Beta — caso contrário pre-rendered offline).
**Arquivos:** novo `Aero_MeshBillboard.java` + integração em `Aero_RenderDistanceCulling`.
**Dep:** investigar se Beta + StationAPI expõem FBO offscreen. Se não → pre-bake pelo aero-machine-maker.
**Critério de pronto:**
- [ ] Sprite render path ativo além do range
- [ ] Visual: silhueta razoável vs. modelo completo
- [ ] Toggle `-Daero.billboard=false`

### B12. LODs reais de modelo (decimation pipeline) — **P2 escopo grande**
**Falta:** hoje LOD = "menos animação". LOD = "menos vértices" exige pipeline: gerar `model.lod1.obj` / `lod2.obj` (ou auto-decimate via quadric error metric no load).
**Arquivos:** novo `Aero_MeshDecimator.java` em core/, integração em `Aero_ObjLoader`.
**Dep:** decidir via tooling (offline) ou runtime (CPU cost). Recomendado offline → `tools/aero-machine-maker` exporta pre-decimated.
**Critério de pronto:**
- [ ] LOD0/LOD1/LOD2 carregados quando presentes
- [ ] Switch baseado em distância
- [ ] Sem fallback degradado quando LOD ausente (continua usando base como hoje)

---

## Grupo C — BE Cell Pages / escala massiva em tela

Objetivo: cenas que caem de ~800 FPS para 150–200 FPS por volume de
BlockEntities devem parar de pagar custo por BE individual. O caminho é
orçamento + células + páginas at-rest, preservando animação real só onde ela
importa.

### C0. Animation render budget — **CONCLUÍDO**
**Entregue:** `Aero_AnimationRenderBudget` reseta no início do render frame
StationAPI e rebaixa overflow `ANIMATED → STATIC`, mantendo o objeto visível
via display-list at-rest/BPDL. Flags:
- `-Daero.animBudget=false`
- `-Daero.maxAnimatedBE=N` (default `128`, `-1` = ilimitado)

**Critério de pronto:**
- [x] Reset por frame no `GameRendererMixin → Aero_RenderDistance.beginRenderFrame`
- [x] Integração no `Aero_RenderDistance.lodRelative`
- [x] Overflow renderiza como STATIC em vez de sumir
- [ ] Métrica visual/JFR em stress com 300+ BEs olhando direto para a fábrica

### C1. Prioridade de animação por importância — **CONCLUÍDO**
**Entregue:** admissão do C0 ficou importance-aware: calcula tamanho projetado
aproximado em pixels, reserva espaço extra para objetos muito próximos/grandes
e rejeita objetos pequenos/longe antes de consumirem o orçamento inteiro.
Também segura por poucos frames objetos que acabaram de entrar como animated,
evitando alternância animated/static na borda do orçamento. Flags:
- `-Daero.animBudget.criticalPx=N` (default `64`)
- `-Daero.animBudget.midPx=N` (default `32`)
- `-Daero.animBudget.lowPx=N` (default `16`)
- `-Daero.animBudget.nearBlocks=N` (default `12`)
- `-Daero.animBudget.criticalExtra=N` (default `max(8, maxAnimatedBE/4)`)
- `-Daero.animBudget.hysteresisFrames=N` (default `6`)
- `-Daero.animBudget.hysteresisExtra=N` (default `max(4, maxAnimatedBE/8)`)

**Nota:** prioridade perfeita ainda fica para o coletor global/células, porque
o dispatcher vanilla continua determinando a ordem de chegada. Este item fecha
o controle local do budget.
**Critério de pronto:**
- [x] Score por tamanho projetado / distância
- [x] Reserva opcional para BEs muito próximos
- [x] Histerese para evitar alternar animado/static a cada frame

### C2. BECellIndex por chunk/célula — **PARCIAL**
**Entregue:** `Aero_CellRenderableBE` no core e `Aero_BECellIndex` no runtime
StationAPI. A base `Aero_RenderDistanceBlockEntity` registra automaticamente
no `tick()`, no `distanceFrom(...)` e em `shouldTickAnimation()`, remove em
`markRemoved()` e reanexa em `cancelRemoval()`. O índice agrupa por mundo +
célula 8³ (`-Daero.becell.size=N`), marca células dirty quando
estado/orientação/eligibilidade muda e expõe snapshot só das células cujo
chunk passa em `Aero_ChunkVisibility`.

**Falta:** integração com o renderer central e hooks para BEs que não herdam
`Aero_RenderDistanceBlockEntity`. O sweep periódico continua como failsafe
para entradas cujo `world` sumiu/mudou.
**Critério de pronto:**
- [x] Interface `Aero_CellRenderableBE`
- [x] Registro/desregistro seguro em load/unload
- [x] Dirty flags para modelo/textura/orientação/light/state
- [x] Iteração só de células cujo chunk passa em `Aero_ChunkVisibility`

### C3. Cell at-rest pages — **PARCIAL**
**Entregue:** `Aero_BECellRenderer.queueAtRest(...)` para renderers StationAPI.
O flush no fim de `WorldRenderer.renderEntities` agrupa instâncias at-rest por
mundo/célula/render-key, compila display lists por célula que chamam as
display lists at-rest do modelo com transform local, e mantém fallback direto
para grupos pequenos, blend/translucência, camera cache ausente ou falha em
`glGenLists`. Flags:
- `-Daero.becell.pages=false`
- `-Daero.becell.minInstances=N` (default `2`)
- `-Daero.becell.pageTtlFrames=N` (default `600`)

**Falta:** migrar todos os BERs consumidores para o `queueAtRest` /
`Aero_CellPageRenderableBE` e adicionar rebuild amortizado real.
**Critério de pronto:**
- [x] Page cache do `Aero_BECellRenderer` com buckets por texture/options/light/orientation
- [ ] Rebuild amortizado `-Daero.becell.rebuildsPerFrame=N`
- [x] Fallback quando `glGenLists` falha
- [x] Dispose em unload/reload

### C4. Central cell flush — **CONCLUÍDO**
**Entregue:** flush no fim do entity pass, ao lado do `Aero_AnimatedBatcher`,
para desenhar páginas de célula em batches ordenados por texture/model/célula.
O mesmo flush é chamado como failsafe no começo do próximo frame caso o mixin
não aplique. O `Aero_Profiler` mede `aero.becell.flush`,
`aero.becell.compile`, `aero.becell.call` e `aero.becell.direct`.
**Critério de pronto:**
- [x] `Aero_BECellRenderer.flush(...)`
- [x] Sort por texture/render options
- [x] Métricas no `Aero_Profiler` e contadores debug (`pageCalls`, `pageRebuilds`, `directFallbacks`)

### C5. Pular render individual dos BEs cell-managed — **CONCLUÍDO**
**Entregue:** contrato `Aero_CellPageRenderableBE` para BEs StationAPI que
expõem modelo/textura/brightness/LOD estático. A base
`Aero_RenderDistanceBlockEntity.distanceFrom(...)` resolve LOD antes do
dispatcher; quando o resultado é STATIC, enfileira a cell page e retorna
`Infinity`, evitando chamar o BER individual. Se páginas estão desativadas,
o modelo é translúcido, o BE não pode ser paginado, ou a fila falha, o caminho
volta ao renderer individual. Toggle: `-Daero.becell.skipIndividual=false`.
**Critério de pronto:**
- [x] Opt-in por interface/flag
- [x] Fallback seguro sem mixin obrigatório
- [x] Sem sumiço visual quando a célula está dirty ou ainda não compilou

---

## Plano de execução

**Próxima onda (P0, batch curto):**
1. ~~B1 — vertex welding~~ → DEFERIDO (mesclado em B2 quando atacarmos)
2. ~~B3 — mipmap em texturas standalone~~ → DEFERIDO (escopo P1, infra de textura)
3. ~~B4 — alpha clipping mode~~ → **CONCLUÍDO**
4. ~~B5 — small-object culling~~ → **CONCLUÍDO**
5. ~~A3 — animated batcher sort por textura~~ → **CONCLUÍDO**

**Onda P0 fechada.** Próxima onda (P1):
6. ~~A1 — chunk `inFrustum` cache~~ → JÁ ENTREGUE pela 3.x (`Aero_ChunkVisibility`)
7. A6 — BE index por chunk
8. ~~A4 — animation curve LUT~~ → **CONCLUÍDO**
9. A5 — composite-key sort no batcher ← **PRÓXIMO**
10. B2 — hidden face removal (engloba B1)
11. B9 — cache no caller (PalettedContainer chunk-scope)

**Onda seguinte (P1, batch médio):**
6. A1 — chunk `inFrustum` cache
7. A6 — BE index por chunk (depende de A1)
8. A4 — animation curve LUT
9. A5 — composite-key sort no batcher
10. B2 — hidden face removal
11. B9 — cache no caller (PalettedContainer chunk-scope)

**Onda final (P2 / escopo grande):**
12. A2 — brightness buckets configuráveis
13. B6 — skeletal LOD intermediário
14. B7 — mesh quantization
15. B10 — motion-based simplification
16. B11 — billboard distante
17. B12 — LODs reais (decimation pipeline)
18. B8 — async chunk meshing (4.0, marco maior)
19. C1–C5 — BE Cell Pages completas

---

## Como vamos iterar

Por iteração:
1. Pegar o próximo item da onda atual (ordem acima)
2. Verificar premissas (arquivos e contratos ainda existem)
3. Implementar com toggle opt-out
4. Rodar `bash scripts/test_unit.sh` no consumer mais próximo (modellib stationapi/test)
5. Rodar `runClientBenchmark -Pbench=60 -Daero.stresstest=true` antes/depois — diff de JFR
6. Marcar checkboxes neste arquivo, atualizar `CHANGELOG.md` na próxima release
7. Próximo item

Se uma premissa falhar (ex.: chunk-mesh API mudou na StationAPI), o item move pra "Bloqueados" abaixo com motivo.

## Bloqueados

(vazio)
