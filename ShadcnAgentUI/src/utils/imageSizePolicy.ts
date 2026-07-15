export interface ImageSizeOption {
  value: string
  label: string
  ratio: string
  ratioLabel: string
}

const genericSizes: ImageSizeOption[] = [
  option('1024x1024', '1 / 1', '1:1'),
  option('1254x1254', '1 / 1', '1:1'),
  option('1672x941', '16 / 9', '16:9'),
  option('1536x1024', '3 / 2', '3:2'),
  option('1024x1536', '2 / 3', '2:3'),
  option('1448x1086', '4 / 3', '4:3'),
  option('1659x948', '7 / 4', '7:4'),
]

const qwen20Sizes: ImageSizeOption[] = [
  option('2048x2048', '1 / 1', '1:1（默认）'),
  option('2688x1536', '16 / 9', '16:9'),
  option('1536x2688', '9 / 16', '9:16'),
  option('2368x1728', '4 / 3', '4:3'),
  option('1728x2368', '3 / 4', '3:4'),
]

const qwenMaxPlusSizes: ImageSizeOption[] = [
  option('1664x928', '16 / 9', '16:9（默认）'),
  option('1472x1104', '4 / 3', '4:3'),
  option('1328x1328', '1 / 1', '1:1'),
  option('1104x1472', '3 / 4', '3:4'),
  option('928x1664', '9 / 16', '9:16'),
]

export function imageSizeOptions(modelName?: string): ImageSizeOption[] {
  const model = modelName?.trim().toLowerCase() ?? ''
  if (model.startsWith('qwen-image-2.0')) return qwen20Sizes
  if (model.startsWith('qwen-image-max') || model.startsWith('qwen-image-plus')) return qwenMaxPlusSizes
  return genericSizes
}

export function defaultImageSize(modelName?: string): string {
  return imageSizeOptions(modelName)[0].value
}

function option(value: string, ratio: string, ratioLabel: string): ImageSizeOption {
  return { value, label: value, ratio, ratioLabel }
}
