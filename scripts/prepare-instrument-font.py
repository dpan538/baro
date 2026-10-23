"""Optional fontTools regeneration; original upstream bytes remain in originals/."""
from pathlib import Path
from fontTools import subset
root=Path(__file__).resolve().parents[1]
assets=root/'dist/assets/fonts'
files=['observatory.js','planning-model.js','sky-model.js','weather-art.js']
text=''.join((root/'dist'/f).read_text() for f in files)
options=subset.Options()
options.flavor='woff2'
font=subset.load_font(str(assets/'originals/NotoSansSC.ttf'),options)
sub=subset.Subsetter(options=options)
sub.populate(text=''.join(sorted(set(text)))+'0123456789—≈Δ°% /')
sub.subset(font)
for record in font['name'].names:
    if record.nameID in [1,2,3,4,6]:
        names={1:'BARO Instrument SC',2:'Regular',3:'BARO Instrument SC subset',4:'BARO Instrument SC',6:'BAROInstrumentSC'}
        record.string=names[record.nameID].encode(record.getEncoding())
subset.save_font(font,str(assets/'InstrumentSC.woff2'),options)
print('Generated renamed InstrumentSC subset; upstream font and OFL preserved.')
