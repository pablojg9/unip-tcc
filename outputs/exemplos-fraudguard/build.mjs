import fs from 'node:fs/promises';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';
const dir = '/Users/pablo/Documents/Unip/unip-tcc/outputs/exemplos-fraudguard';
const headers = ['id_sinistro','tipo_sinistro','valor_sinistro','valor_franquia','estado','cidade','dias_ate_aviso','sinistros_ultimos_12m','boletim_ocorrencia','oficina_rede','canal_aviso','descricao','fraude'];
let seed = 20261030;
const rand = () => { seed = (1664525 * seed + 1013904223) >>> 0; return seed / 4294967296; };
for (const [name,count,training] of [['sinistros_analise',24,false]]) {
  const rows = [];
  for (let i=0;i<count;i++) {
    const fraud = (i * 37) % 160 < 40;
    const suspicious = fraud ? rand() > .15 : rand() < .12;
    rows.push([`${training?'TREINO':'ANALISE'}-20261029-${String(i+1).padStart(4,'0')}`, ['Colisao','Furto','Roubo','Vidros'][i%4],
      Math.round(1500+rand()*(suspicious?85000:35000)), [0,500,1500,2500][Math.floor(rand()*4)], 'SP','Campinas',
      Math.floor(rand()*(suspicious?28:8)), Math.floor(rand()*(suspicious?6:2)), suspicious?rand()>.7:rand()>.15,
      suspicious?rand()>.7:rand()>.15,['Aplicativo','Telefone','Corretor'][Math.floor(rand()*3)],
      'Registro sintetico para demonstracao do sistema', fraud?1:0]);
  }
  const cols=headers;
  const wb=Workbook.create(); const sh=wb.worksheets.add('Sinistros');
  sh.getRange(`A1:M${count+1}`).values=[cols,...rows];
  sh.getRange(`A1:M${count+1}`).format.columnWidth=23;
  sh.getRange(`A1:M1`).format={fill:'#087E8B',font:{bold:true,color:'#FFFFFF'},rowHeight:32};
  sh.getRange(`L1:L${count+1}`).format.columnWidth=54;
  sh.getRange(`C2:D${count+1}`).setNumberFormat('#,##0.00');
  wb.recalculate();
  console.log((await wb.inspect({kind:'table',range:'Sinistros!A1:D4',tableMaxRows:4,tableMaxCols:4})).ndjson);
  const preview=await wb.render({sheetName:'Sinistros',range:'A1:F6',scale:1.5});
  await fs.writeFile(`${dir}/${name}.png`,new Uint8Array(await preview.arrayBuffer()));
  await (await SpreadsheetFile.exportXlsx(wb)).save(`${dir}/${name}.xlsx`);
  console.log(name,rows.length,'rows',training?'40 fraudes / 120 legitimos':'com rotulo fraude');
}
