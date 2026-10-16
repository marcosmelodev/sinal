-- Catálogo oficial de ocorrências (Etapa 5 §10): 11 por órgão, com códigos estáveis.
-- "Outro / Não sei informar" usa ordem 99 para permanecer sempre por último.

INSERT INTO ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES
    ('PM_ASSALTO_ROUBO',                'POLICIA_MILITAR', 'Assalto / Roubo em andamento',              1, TRUE),
    ('PM_AGRESSAO_BRIGA',               'POLICIA_MILITAR', 'Agressão física / Briga',                   2, TRUE),
    ('PM_VIOLENCIA_DOMESTICA',          'POLICIA_MILITAR', 'Violência doméstica / Pessoa em perigo',    3, TRUE),
    ('PM_INVASAO_DOMICILIO',            'POLICIA_MILITAR', 'Invasão de domicílio / Estabelecimento',    4, TRUE),
    ('PM_AMEACA_ARMA_FOGO',             'POLICIA_MILITAR', 'Ameaça de morte / Arma de fogo',            5, TRUE),
    ('PM_FURTO_VEICULO_OBJETO',         'POLICIA_MILITAR', 'Furto de veículo / Objeto',                 6, TRUE),
    ('PM_DISPAROS_ARMA_FOGO',           'POLICIA_MILITAR', 'Disparos de arma de fogo',                  7, TRUE),
    ('PM_PESSOA_SUSPEITA',              'POLICIA_MILITAR', 'Pessoa suspeita rondando',                  8, TRUE),
    ('PM_DESORDEM_PERTURBACAO',         'POLICIA_MILITAR', 'Desordem / Perturbação do sossego',         9, TRUE),
    ('PM_ACIDENTE_TRANSITO_BRIGA_FUGA', 'POLICIA_MILITAR', 'Acidente de trânsito com briga / Fuga',    10, TRUE),
    ('PM_OUTRO',                        'POLICIA_MILITAR', 'Outro / Não sei informar',                 99, TRUE);

INSERT INTO ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES
    ('SAMU_DESACORDADO_NAO_RESPIRA',   'SAMU', 'Pessoa desacordada / Não respira',              1, TRUE),
    ('SAMU_DOR_PEITO_INFARTO',         'SAMU', 'Dor forte no peito / Suspeita de infarto',      2, TRUE),
    ('SAMU_FALTA_AR_ENGASGO',          'SAMU', 'Falta de ar grave / Engasgo',                   3, TRUE),
    ('SAMU_CONVULSAO',                 'SAMU', 'Convulsão / Ataque epiléptico',                 4, TRUE),
    ('SAMU_SANGRAMENTO_GRAVE',         'SAMU', 'Sangramento grave / Hemorragia',                5, TRUE),
    ('SAMU_QUEDA_FRATURA',             'SAMU', 'Queda de altura com fratura',                   6, TRUE),
    ('SAMU_SUSPEITA_AVC',              'SAMU', 'Boca torta / Perda de força (Suspeita de AVC)', 7, TRUE),
    ('SAMU_QUEIMADURA_GRAVE',          'SAMU', 'Queimadura grave',                              8, TRUE),
    ('SAMU_INTOXICACAO_ENVENENAMENTO', 'SAMU', 'Intoxicação / Envenenamento',                   9, TRUE),
    ('SAMU_TENTATIVA_SUICIDIO',        'SAMU', 'Tentativa de suicídio',                        10, TRUE),
    ('SAMU_OUTRO',                     'SAMU', 'Outro / Não sei informar',                     99, TRUE);

INSERT INTO ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES
    ('CBM_INCENDIO_RESIDENCIA_PREDIO', 'CORPO_DE_BOMBEIROS', 'Incêndio em residência / Prédio',                 1, TRUE),
    ('CBM_INCENDIO_MATA_VEGETACAO',    'CORPO_DE_BOMBEIROS', 'Incêndio em mata / Vegetação',                    2, TRUE),
    ('CBM_ACIDENTE_PRESO_FERRAGENS',   'CORPO_DE_BOMBEIROS', 'Acidente com pessoa presa nas ferragens',         3, TRUE),
    ('CBM_VAZAMENTO_GAS',              'CORPO_DE_BOMBEIROS', 'Vazamento de gás com cheiro forte',               4, TRUE),
    ('CBM_AFOGAMENTO',                 'CORPO_DE_BOMBEIROS', 'Afogamento / Vítima em rio ou piscina',           5, TRUE),
    ('CBM_DESABAMENTO',                'CORPO_DE_BOMBEIROS', 'Desabamento / Risco de queda de estrutura',       6, TRUE),
    ('CBM_CHOQUE_ELETRICO',            'CORPO_DE_BOMBEIROS', 'Choque elétrico / Fiação caída na rua',           7, TRUE),
    ('CBM_QUEDA_ARVORE',               'CORPO_DE_BOMBEIROS', 'Queda de árvore sobre casa ou via',               8, TRUE),
    ('CBM_ATAQUE_ANIMAIS',             'CORPO_DE_BOMBEIROS', 'Ataque de animais perigosos',                     9, TRUE),
    ('CBM_PESSOA_ANIMAL_PRESO',        'CORPO_DE_BOMBEIROS', 'Pessoa ou animal preso em local de risco',       10, TRUE),
    ('CBM_OUTRO',                      'CORPO_DE_BOMBEIROS', 'Outro / Não sei informar',                       99, TRUE);
