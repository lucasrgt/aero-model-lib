# Roadmap de performance - AeroModelLib

Snapshot para investigação de gargalos. Este arquivo separa o que já está
implementado na lib, o que ainda depende de adoção nos mods consumidores, e o
que precisa de benchmark/JFR antes de virar prioridade.

Última limpeza de status: 2026-05-01.

## Legenda

- **CONCLUÍDO:** código entregue, build/testes locais passando, toggle/fallback
  quando aplicável.
- **INFRA CONCLUÍDA / ADOÇÃO PARCIAL:** a lib já tem o mecanismo, mas mods
  consumidores ainda precisam implementar a interface ou chamar a API.
- **ABERTO:** não implementado.
- **DEFERIDO:** ideia mantida, mas não é bom próximo passo sem outro
  pré-requisito ou evidência de benchmark.
- **BENCH PENDENTE:** falta medir em cena real/JFR. Não significa que o código
  não existe.

## Validação atual

Validado nesta leva:
- `modloader/tests/run.ps1`: 214 testes passando.
- `stationapi`: `compileJava remapJar` passando.
- `stationapi/test`: `compileJava` passando.

Ainda pendente antes de declarar ganho real em produção:
- Rodar benchmark visual/stress com muitos BEs, preferencialmente comparando
  antes/depois por JFR.
- Conferir adoção em mods consumidores reais, especialmente Beta Energistics.

## Restrições de plataforma

- Minecraft Beta 1.7.3 + StationAPI/Babric.
- LWJGL 2 / OpenGL 1.x fixed-function.
- Sem shaders programáveis, instancing moderno, compute, MRT ou indirect draw.
- Canais reais de submissão: Tessellator vanilla e display lists.
- Qualquer técnica nova precisa ter fallback conservador e opt-out por flag.

---

## Resumo Para Próxima Análise

O próximo gargalo provável não é mais "como emitir um modelo animado isolado",
porque BPDL, animated batcher e cell pages já cobrem boa parte disso. O foco
agora deve ser descobrir, com benchmark/JFR, qual destes custos sobrou no topo:

1. Adoção incompleta de `Aero_CellPageRenderableBE` nos mods reais.
2. Iteração vanilla por todos os BEs antes do skip por `distanceFrom`.
3. Troca de estado GL dentro do animated batcher.
4. Triângulos desnecessários em OBJ grande.
5. Chunk meshing / `PalettedContainer.get` em entrada de mundo ou chunk novo.

---

## Grupo A - Render Queue, Culling e Estado GL

### A1. Chunk visibility / PVS por `inFrustum` - **CONCLUÍDO, BENCH PENDENTE**

**Entregue:** `Aero_ChunkVisibility` tira snapshot de
`WorldRenderer.chunks[]` via `WorldRendererChunksAccessor`, usa `inFrustum` e
o resultado de hardware occlusion query quando disponível, e faz
`Aero_RenderDistance.blockEntityDistanceFrom(...)` retornar `Infinity` para
BEs em chunk não visível.

**Notas importantes:**
- O snapshot roda no começo do frame e lê o estado do frame anterior. Isso é
  intencional para evitar mixin frágil em `WorldRenderer.cullChunks`.
- A chave colapsa Y e usa `(chunkX, chunkZ)`, adequado ao mundo Beta 16x128x16
  dividido em chunk-builders 16x16x16.

**Toggle:** `-Daero.chunkvisibility=false`.

**Falta medir:**
- JFR confirmando queda proporcional em `Aero_Frustum6Plane` / dispatcher BE
  quando a câmera olha para regiões com muitos chunks fora do frustum.

### A2. Brightness buckets configuráveis/interpolados - **ABERTO P2**

**Hoje:** display lists at-rest usam 4 buckets de brightness via
`Aero_MeshModel.BRIGHTNESS_FACTORS`.

**Possíveis caminhos:**
- 8 buckets: transição visual melhor, mais listas em driver/VRAM.
- 4 buckets + interpolação de cor no draw: menor memória, mais chamadas/cor.

**Critério para atacar:** só se o Pro ou teste visual encontrar banding de luz
como problema real.

### A3. Animated batcher sort por textura - **CONCLUÍDO**

**Entregue:** `Aero_AnimatedBatcher` ordena batches por `texturePath` quando
há mais de um batch e deduplica binds adjacentes com `lastBoundPath`.

**Toggle:** `-Daero.batcher.sort=false`.

**Falta medir:** métrica direta de `glBindTexture`; hoje a prova forte ainda
depende de JFR/RenderDoc ou instrumentação futura.

### A4. Animation curve LUT bake - **CONCLUÍDO, OPT-IN**

**Entregue:** `Aero_AnimationLUTConfig` e LUT por canal quando
`-Daero.anim.lut=true`. Mantém fallback para sampling original.

**Flags:**
- `-Daero.anim.lut=true`
- `-Daero.anim.lut.samples=N` (default `64`)

**Falta medir:** JFR em stress real para confirmar o quanto o custo de easing /
binary search / slerp caiu.

### A5. Composite-key sort do animated batcher - **ABERTO P1**

**Hoje:** o batcher ordena por textura, mas não por estado GL completo.

**Falta:** ordenar também por chave composta de estado:
`texturePath + blend + depthTest + cullFaces + alphaClip + tint/alpha`.

**Por que pode importar:** reduz `glEnable/glDisable`, `glBlendFunc`,
`glAlphaFunc` e mudanças de estado quando muitos modelos animados têm opções
diferentes.

**Critério para atacar:** JFR/trace mostrando troca de estado GL como custo
relevante depois de cell pages.

### A6. Índice global para não iterar BE vanilla - **PARCIAL / REFORMULADO**

**Entregue parcialmente:** `Aero_BECellIndex` mantém BEs opt-in agrupados por
célula 8³ e mundo, com dirty flags de state/orientation/canCellPage.

**O que C5 já resolveu:** BEs que herdam `Aero_RenderDistanceBlockEntity` e
implementam `Aero_CellPageRenderableBE` podem ser enfileirados no
`distanceFrom(...)` e pular o renderer individual.

**O que ainda NÃO está resolvido:** vanilla ainda itera a lista de BEs e chama
`distanceFrom(...)`. Não há ainda um dispatcher próprio que itere só células
visíveis sem passar pela lista vanilla.

**Próximo passo só se JFR justificar:**
- mixin no dispatcher/lista de BEs para pular em lote, ou
- render pass próprio por célula e supressão segura do dispatcher vanilla para
  BEs Aero-managed.

---

## Grupo B - Mesh, OBJ e Chunk Meshing

### B1. OBJ vertex welding - **DEFERIDO, SUBTAREFA DE B2**

**Premissa:** welding sozinho não reduz upload, porque a malha interna é
triângulo flatten em `float[]` e o renderer não usa index buffer.

**Valor real:** pré-requisito para detectar faces internas em B2.

### B2. OBJ hidden face removal - **ABERTO P1**

**Falta:** remover faces totalmente internas de modelos OBJ compostos por
blocos/caixas adjacentes.

**Cuidado:** opt-in obrigatório. Alguns modelos esperam que a câmera veja
interior ou backfaces.

**Flag proposta:** `-Daero.obj.cullhidden=true` default off.

**Critério para atacar:** modelo grande com redução de faces mensurável e sem
diff visual em câmera externa.

### B3. Mipmap em texturas de modelo - **DEFERIDO**

**Motivo:** a modellib não possui loader de textura próprio; ela chama o
`TextureManager` vanilla via path. Mipmap exigiria mixin global no
TextureManager ou loader paralelo LWJGL, ambos com risco visual alto em textura
pixel-art.

### B4. Alpha clipping em `Aero_RenderOptions` - **CONCLUÍDO**

**Entregue:** `alphaClip(threshold)`, `Aero_RenderOptions.alphaClipped(...)` e
estado GL restaurado em ambos os runtimes.

### B5. Small-object culling - **CONCLUÍDO**

**Entregue:** `Aero_SmallObjectCull` integrado em `shouldRenderRelative`,
`lodRelative` e `blockEntityDistanceFrom`.

**Flags:**
- `-Daero.smallobj=false`
- `-Daero.smallobj.px=N`

### B6. Skeletal LOD intermediário - **ABERTO P2**

**Ideia:** tier entre ANIMATED e STATIC que anima só ossos acima de certa
profundidade na hierarquia.

**Critério para atacar:** modelos com hierarquia profunda onde BPDL ainda deixa
pose/chain cost alto.

### B7. Mesh quantization - **ABERTO P2**

**Ideia:** guardar posições/UV/normais em representação quantizada para reduzir
heap. Não acelera display list diretamente; é otimização de memória.

### B8. Async chunk meshing - **ABERTO P1 GRANDE**

**Problema alvo:** entrada de mundo/chunk novo, não exatamente render steady
state da modellib.

**Risco:** contrato vanilla/StationAPI e sincronização de mundo/chunks.

**Flag proposta:** `-Daero.chunkmesh.async=true` default off.

### B9. Cache chunk-scope no caller de `PalettedContainer.get` - **ABERTO P1**

**Ideia correta:** cache com vida limitada ao build de chunk, ativado no caller
de `WorldRenderer.compileChunks`, não em cada `PalettedContainer.get`.

**Depende de:** desenho final de B8 se o meshing virar async.

### B10. Motion-based animation simplification - **ABERTO P2**

**Ideia:** reduzir tick/pose quando entidade/BE móvel está rápido demais para o
olho perceber pose precisa.

### B11. Billboard distante - **ABERTO P2**

**Ideia:** trocar at-rest distante por sprite/silhueta quando distância passa
do range útil do modelo real.

**Cuidado:** FBO/offscreen em Beta precisa ser investigado; alternativa é
pre-bake offline via tooling.

### B12. LODs reais de modelo / decimation - **ABERTO P2 GRANDE**

**Ideia:** carregar `model.lod1.obj`, `model.lod2.obj` ou gerar decimation
offline. Melhor como pipeline/tooling do que em runtime.

---

## Grupo C - BE Cell Pages / Escala Massiva em Tela

Objetivo: quando uma cena cai de ~800 FPS para 150-200 FPS por volume de
BlockEntities, reduzir trabalho por BE individual. A lib agora tem orçamento
de animação, páginas por célula e skip do renderer individual para BEs opt-in.

### C0. Animation render budget - **CONCLUÍDO**

**Entregue:** `Aero_AnimationRenderBudget` rebaixa overflow
`ANIMATED -> STATIC`, mantendo o objeto visível.

**Flags:**
- `-Daero.animBudget=false`
- `-Daero.maxAnimatedBE=N` (default `128`, `-1` ilimitado)

**Detalhe importante:** decisões por chave são memoizadas no frame para não
consumir budget duas vezes quando `distanceFrom(...)` e renderer perguntam LOD.

### C1. Prioridade de animação por importância - **CONCLUÍDO**

**Entregue:** prioridade por tamanho projetado/distância, reserva crítica para
objetos próximos/grandes e histerese para evitar flicker animated/static.

**Flags principais:**
- `-Daero.animBudget.criticalPx=N`
- `-Daero.animBudget.midPx=N`
- `-Daero.animBudget.lowPx=N`
- `-Daero.animBudget.nearBlocks=N`
- `-Daero.animBudget.criticalExtra=N`
- `-Daero.animBudget.hysteresisFrames=N`
- `-Daero.animBudget.hysteresisExtra=N`

### C2. BECellIndex por célula - **INFRA CONCLUÍDA / ADOÇÃO PARCIAL**

**Entregue:** `Aero_CellRenderableBE` e `Aero_BECellIndex` agrupam BEs opt-in
por mundo+célula, com dirty flags e snapshot de células visíveis.

**Adoção atual:** automática para classes que herdam
`Aero_RenderDistanceBlockEntity`. BEs que não herdam essa base ainda precisam
chamar track/untrack ou ganhar hooks específicos.

**Falta para consumidores reais:** migrar BEs relevantes do Beta Energistics e
outros mods para a base/contrato.

### C3. Cell at-rest pages - **INFRA CONCLUÍDA / ADOÇÃO PARCIAL**

**Entregue:** `Aero_BECellRenderer.queueAtRest(...)` e
`Aero_CellPageRenderableBE`. O flush compila display lists por célula que
chamam as display lists at-rest do modelo com transform local.

**Flags:**
- `-Daero.becell.pages=false`
- `-Daero.becell.minInstances=N` (default `2`)
- `-Daero.becell.pageTtlFrames=N` (default `600`)
- `-Daero.becell.rebuildsPerFrame=N` (default `8`, `-1` ilimitado)

**Fallbacks:** grupo pequeno, blend/translucência, falha de `glGenLists`,
modelo não paginável ou flag desligada voltam para render direto/individual.

**Falta para consumidores reais:** implementar `Aero_CellPageRenderableBE` nos
BEs que podem aparecer como at-rest/LOD-overflow.

### C4. Central cell flush - **CONCLUÍDO**

**Entregue:** `Aero_BECellRenderer.flush(...)` no fim de
`WorldRenderer.renderEntities`, junto ao `Aero_AnimatedBatcher`, com failsafe
no começo do próximo frame.

**Profiler:** `aero.becell.flush`, `aero.becell.compile`,
`aero.becell.call`, `aero.becell.direct`.

### C5. Skip do renderer individual para BEs cell-managed - **CONCLUÍDO**

**Entregue:** `Aero_RenderDistanceBlockEntity.distanceFrom(...)` resolve LOD;
quando o BE implementa `Aero_CellPageRenderableBE` e cai em STATIC, a base
enfileira a cell page e retorna `Infinity` para vanilla, evitando o BER
individual.

**Toggle:** `-Daero.becell.skipIndividual=false`.

**Limite atual:** ainda existe custo de iteração vanilla e chamada
`distanceFrom(...)`. O que sai é o custo do renderer individual e da emissão
por BE quando o BE é paginável.

---

## Grupo D - Bone Pages / Animação Rígida

### D1. Bone-page display lists - **CONCLUÍDO**

**Entregue:** BPDL para grupos rígidos/named groups. A geometria de grupos
elegíveis vira display list por bone/bucket; render animado aplica matriz do
bone e chama `glCallList`. UV animation usa matriz de textura fixed-function.

**Flags:**
- `-Daero.bonepages=false`
- `-Daero.bonepages.minTris=N` (default `24`)

**Fallbacks:** morph ativo, grupo pequeno, falha de list, ou geometria não
elegível voltam ao Tessellator.

**Profiler:** `aero.bonepages.compile`, `aero.bonepages.call`.

---

## Próxima Onda Recomendada

Antes de implementar técnica nova, medir:

1. Rodar stress/JFR com muitos BEs reais, incluindo Beta Energistics.
2. Comparar flags:
   - baseline atual
   - `-Daero.becell.skipIndividual=false`
   - `-Daero.becell.pages=false`
   - `-Daero.bonepages=false`
   - `-Daero.maxAnimatedBE=-1`
3. Confirmar se o topo do JFR é renderer individual, `distanceFrom`,
   `Aero_AnimatedBatcher`, estado GL, OBJ tri count ou chunk meshing.

Próximas implementações prováveis, em ordem conservadora:

1. Migrar BEs reais do Beta Energistics para `Aero_CellPageRenderableBE`.
2. A5 - composite-key sort do animated batcher, se estado GL aparecer no topo.
3. B2 - hidden face removal, se triângulo/modelo OBJ aparecer no topo.
4. B9 - cache chunk-scope, se entrada de mundo/chunk build aparecer no topo.
5. A6 - dispatcher/cell iteration mais invasivo, se a iteração vanilla por BE
   continuar cara mesmo depois do skip individual.

## Bloqueados

Nenhum bloqueio confirmado no momento. Itens grandes dependem de benchmark para
evitar otimizar o gargalo errado.
