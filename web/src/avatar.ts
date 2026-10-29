/** Lado da foto enviada: o core também limita a isso (e re-codifica), aqui só evita mandar 5 MB. */
const SIZE = 512;

const MAX_FILE_BYTES = 20 * 1024 * 1024;

/**
 * Prepara a foto de perfil no navegador: recorte quadrado central, no máximo 512px, JPEG.
 * O navegador decodifica o que ele sabe abrir (WebP, HEIC no Safari) e já aplica a rotação do
 * EXIF de fotos de celular; o core só precisa aceitar JPEG.
 *
 * @throws Error com mensagem pronta para mostrar ao usuário
 */
export async function prepareAvatar(file: File): Promise<Blob> {
  if (!file.type.startsWith("image/")) {
    throw new Error("Escolha um arquivo de imagem.");
  }
  if (file.size > MAX_FILE_BYTES) {
    throw new Error("Imagem grande demais (máximo de 20 MB).");
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error("Não foi possível abrir essa imagem. Use JPG, PNG ou WebP.");
  }

  try {
    const side = Math.min(bitmap.width, bitmap.height);
    const size = Math.min(side, SIZE);
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("Seu navegador não conseguiu processar a imagem.");
    }
    // PNG transparente vira fundo branco (JPEG não tem transparência)
    context.fillStyle = "#fff";
    context.fillRect(0, 0, size, size);
    context.imageSmoothingQuality = "high";
    context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error("Seu navegador não conseguiu processar a imagem."))),
        "image/jpeg",
        0.9,
      ),
    );
  } finally {
    bitmap.close();
  }
}
