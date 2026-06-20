<script>
  // Affiche une image-icône (bibliothèque sp_icone) si imageId est défini, sinon l'emoji en repli.
  // L'endpoint /contenu est anonyme → un simple <img src> suffit (pas de header d'auth).
  let { imageId = null, emoji = '', size = 20, alt = '' } = $props()
</script>

{#if imageId}
  <img class="icone-img" src={`/api/sp/icones/${imageId}/contenu`} alt={alt}
       style={`width:${size}px;height:${size}px`} />
{:else}
  <span class="icone-emoji" style={`font-size:${size}px`}>{emoji}</span>
{/if}

<style>
  .icone-img { object-fit: contain; vertical-align: middle; display: inline-block; }
  .icone-emoji { display: inline-block; line-height: 1; }
</style>
