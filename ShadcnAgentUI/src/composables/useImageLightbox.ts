import { ref } from 'vue'

export function useImageLightbox() {
  const lightboxOpen = ref(false)
  const lightboxUrl = ref('')
  const lightboxAlt = ref('Image preview')

  function openLightbox(url: string, alt: string = 'Image preview') {
    if (!url) return
    lightboxUrl.value = url
    lightboxAlt.value = alt
    lightboxOpen.value = true
  }

  return { lightboxOpen, lightboxUrl, lightboxAlt, openLightbox }
}