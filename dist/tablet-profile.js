// Device pixels are a validation target, never a CSS width or an assumed WebView DPR.
export const XIAOMI_PAD_9_PRO_MAX=Object.freeze({
 name:'Xiaomi Pad 9 Pro Max',pixelWidth:3408,pixelHeight:2272,
 referencePixelsPerCssPixel:2,
 source:'https://new.c.mi.com/ua/post/106256'
});
export function tabletReferenceViewport(orientation='landscape',pixelsPerCssPixel=2){
 if(!['landscape','portrait'].includes(orientation)||!Number.isFinite(pixelsPerCssPixel)||pixelsPerCssPixel<=0)throw new RangeError('Invalid tablet reference viewport');
 const {pixelWidth,pixelHeight}=XIAOMI_PAD_9_PRO_MAX;
 return {width:(orientation==='portrait'?pixelHeight:pixelWidth)/pixelsPerCssPixel,height:(orientation==='portrait'?pixelWidth:pixelHeight)/pixelsPerCssPixel};
}
