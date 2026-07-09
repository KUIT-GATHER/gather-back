-- V3: region 시드 데이터 (2026-07-06)
-- 1365 자원봉사포털 실 서비스키로 getVltrSearchWordList 전수 스캔(무필터, 8,918건, 90페이지)해
-- 실제 API가 반환하는 sidoCd/gugunCd 값(code 컬럼)을 확정하고,
-- 행정표준코드관리시스템(code.go.kr) 기관코드 전체자료 다운로드본으로 이름을 대조 확정했다.
-- 주의: 강원도(6420000)/전라북도(6450000)/광주광역시(6290000)는 최근 개편(강원특별자치도·전북특별자치도 전환,
-- 2026-07-01자 광주·전남 통합)으로 마스터DB에서는 폐지 처리됐으나, 1365는 아직 구코드를 그대로 반환하므로
-- 구코드/구명칭 그대로 시드한다(1365 실 응답과의 매칭이 목적이므로 마스터DB의 폐지여부는 무관).

-- 1) 시도(level=1)
INSERT INTO region (name, level, code, parent_id) VALUES ('세종특별자치시', 1, '5690000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('서울특별시', 1, '6110000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('부산광역시', 1, '6260000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('대구광역시', 1, '6270000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('인천광역시', 1, '6280000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('광주광역시', 1, '6290000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('대전광역시', 1, '6300000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('울산광역시', 1, '6310000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('경기도', 1, '6410000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('강원도', 1, '6420000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('충청북도', 1, '6430000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('충청남도', 1, '6440000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('전라북도', 1, '6450000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('전라남도', 1, '6460000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('경상북도', 1, '6470000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('경상남도', 1, '6480000', NULL);
INSERT INTO region (name, level, code, parent_id) VALUES ('제주특별자치도', 1, '6500000', NULL);

-- 2) 시군구(level=2), parent는 시도 code로 조회하여 연결

-- 세종특별자치시
INSERT INTO region (name, level, code, parent_id) SELECT '세종특별자치시', 2, '5690001', id FROM region WHERE code = '5690000';

-- 서울특별시
INSERT INTO region (name, level, code, parent_id) SELECT '종로구', 2, '3000000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '중구', 2, '3010000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '용산구', 2, '3020000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '성동구', 2, '3030000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '광진구', 2, '3040000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '동대문구', 2, '3050000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '중랑구', 2, '3060000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '성북구', 2, '3070000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '강북구', 2, '3080000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '도봉구', 2, '3090000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '노원구', 2, '3100000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '은평구', 2, '3110000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '서대문구', 2, '3120000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '마포구', 2, '3130000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '양천구', 2, '3140000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '강서구', 2, '3150000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '구로구', 2, '3160000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '금천구', 2, '3170000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '영등포구', 2, '3180000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '동작구', 2, '3190000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '관악구', 2, '3200000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '서초구', 2, '3210000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '강남구', 2, '3220000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '송파구', 2, '3230000', id FROM region WHERE code = '6110000';
INSERT INTO region (name, level, code, parent_id) SELECT '강동구', 2, '3240000', id FROM region WHERE code = '6110000';

-- 부산광역시
INSERT INTO region (name, level, code, parent_id) SELECT '중구', 2, '3250000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '서구', 2, '3260000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '동구', 2, '3270000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '영도구', 2, '3280000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '부산진구', 2, '3290000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '동래구', 2, '3300000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '남구', 2, '3310000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '북구', 2, '3320000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '해운대구', 2, '3330000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '사하구', 2, '3340000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '금정구', 2, '3350000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '강서구', 2, '3360000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '연제구', 2, '3370000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '수영구', 2, '3380000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '사상구', 2, '3390000', id FROM region WHERE code = '6260000';
INSERT INTO region (name, level, code, parent_id) SELECT '기장군', 2, '3400000', id FROM region WHERE code = '6260000';

-- 대구광역시
INSERT INTO region (name, level, code, parent_id) SELECT '중구', 2, '3410000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '동구', 2, '3420000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '서구', 2, '3430000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '남구', 2, '3440000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '북구', 2, '3450000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '수성구', 2, '3460000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '달서구', 2, '3470000', id FROM region WHERE code = '6270000';
INSERT INTO region (name, level, code, parent_id) SELECT '달성군', 2, '3480000', id FROM region WHERE code = '6270000';

-- 인천광역시
INSERT INTO region (name, level, code, parent_id) SELECT '중구', 2, '3490000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '동구', 2, '3500000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '남구', 2, '3510000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '연수구', 2, '3520000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '남동구', 2, '3530000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '부평구', 2, '3540000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '계양구', 2, '3550000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '서구', 2, '3560000', id FROM region WHERE code = '6280000';
INSERT INTO region (name, level, code, parent_id) SELECT '강화군', 2, '3570000', id FROM region WHERE code = '6280000';

-- 광주광역시
INSERT INTO region (name, level, code, parent_id) SELECT '동구', 2, '3590000', id FROM region WHERE code = '6290000';
INSERT INTO region (name, level, code, parent_id) SELECT '서구', 2, '3600000', id FROM region WHERE code = '6290000';
INSERT INTO region (name, level, code, parent_id) SELECT '남구', 2, '3610000', id FROM region WHERE code = '6290000';
INSERT INTO region (name, level, code, parent_id) SELECT '북구', 2, '3620000', id FROM region WHERE code = '6290000';
INSERT INTO region (name, level, code, parent_id) SELECT '광산구', 2, '3630000', id FROM region WHERE code = '6290000';

-- 대전광역시
INSERT INTO region (name, level, code, parent_id) SELECT '동구', 2, '3640000', id FROM region WHERE code = '6300000';
INSERT INTO region (name, level, code, parent_id) SELECT '중구', 2, '3650000', id FROM region WHERE code = '6300000';
INSERT INTO region (name, level, code, parent_id) SELECT '서구', 2, '3660000', id FROM region WHERE code = '6300000';
INSERT INTO region (name, level, code, parent_id) SELECT '유성구', 2, '3670000', id FROM region WHERE code = '6300000';
INSERT INTO region (name, level, code, parent_id) SELECT '대덕구', 2, '3680000', id FROM region WHERE code = '6300000';

-- 울산광역시
INSERT INTO region (name, level, code, parent_id) SELECT '중구', 2, '3690000', id FROM region WHERE code = '6310000';
INSERT INTO region (name, level, code, parent_id) SELECT '남구', 2, '3700000', id FROM region WHERE code = '6310000';
INSERT INTO region (name, level, code, parent_id) SELECT '동구', 2, '3710000', id FROM region WHERE code = '6310000';
INSERT INTO region (name, level, code, parent_id) SELECT '북구', 2, '3720000', id FROM region WHERE code = '6310000';
INSERT INTO region (name, level, code, parent_id) SELECT '울주군', 2, '3730000', id FROM region WHERE code = '6310000';

-- 경기도
INSERT INTO region (name, level, code, parent_id) SELECT '수원시', 2, '3740000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '성남시', 2, '3780000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '의정부시', 2, '3820000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '안양시', 2, '3830000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '부천시', 2, '3860000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '광명시', 2, '3900000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '평택시', 2, '3910000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '동두천시', 2, '3920000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '안산시', 2, '3930000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '고양시', 2, '3940000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '과천시', 2, '3970000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '구리시', 2, '3980000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '남양주시', 2, '3990000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '오산시', 2, '4000000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '시흥시', 2, '4010000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '군포시', 2, '4020000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '의왕시', 2, '4030000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '하남시', 2, '4040000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '용인시', 2, '4050000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '파주시', 2, '4060000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '이천시', 2, '4070000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '안성시', 2, '4080000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '김포시', 2, '4090000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '연천군', 2, '4140000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '가평군', 2, '4160000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '양평군', 2, '4170000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '화성시', 2, '5530000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '광주시', 2, '5540000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '양주시', 2, '5590000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '포천시', 2, '5600000', id FROM region WHERE code = '6410000';
INSERT INTO region (name, level, code, parent_id) SELECT '여주시', 2, '5700000', id FROM region WHERE code = '6410000';

-- 강원도
INSERT INTO region (name, level, code, parent_id) SELECT '춘천시', 2, '4180000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '원주시', 2, '4190000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '강릉시', 2, '4200000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '동해시', 2, '4210000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '태백시', 2, '4220000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '속초시', 2, '4230000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '삼척시', 2, '4240000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '홍천군', 2, '4250000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '횡성군', 2, '4260000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '영월군', 2, '4270000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '평창군', 2, '4280000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '정선군', 2, '4290000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '양구군', 2, '4320000', id FROM region WHERE code = '6420000';
INSERT INTO region (name, level, code, parent_id) SELECT '인제군', 2, '4330000', id FROM region WHERE code = '6420000';

-- 충청북도
INSERT INTO region (name, level, code, parent_id) SELECT '충주시', 2, '4390000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '제천시', 2, '4400000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '옥천군', 2, '4430000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '영동군', 2, '4440000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '괴산군', 2, '4460000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '음성군', 2, '4470000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '증평군', 2, '5570000', id FROM region WHERE code = '6430000';
INSERT INTO region (name, level, code, parent_id) SELECT '청주시', 2, '5710000', id FROM region WHERE code = '6430000';

-- 충청남도
INSERT INTO region (name, level, code, parent_id) SELECT '천안시', 2, '4490000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '공주시', 2, '4500000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '보령시', 2, '4510000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '아산시', 2, '4520000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '서산시', 2, '4530000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '논산시', 2, '4540000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '부여군', 2, '4570000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '서천군', 2, '4580000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '청양군', 2, '4590000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '홍성군', 2, '4600000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '예산군', 2, '4610000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '태안군', 2, '4620000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '당진시', 2, '4630000', id FROM region WHERE code = '6440000';
INSERT INTO region (name, level, code, parent_id) SELECT '계룡시', 2, '5580000', id FROM region WHERE code = '6440000';

-- 전라북도
INSERT INTO region (name, level, code, parent_id) SELECT '전주시', 2, '4640000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '군산시', 2, '4670000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '익산시', 2, '4680000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '정읍시', 2, '4690000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '남원시', 2, '4700000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '김제시', 2, '4710000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '완주군', 2, '4720000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '진안군', 2, '4730000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '임실군', 2, '4760000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '순창군', 2, '4770000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '고창군', 2, '4780000', id FROM region WHERE code = '6450000';
INSERT INTO region (name, level, code, parent_id) SELECT '부안군', 2, '4790000', id FROM region WHERE code = '6450000';

-- 전라남도
INSERT INTO region (name, level, code, parent_id) SELECT '목포시', 2, '4800000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '여수시', 2, '4810000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '순천시', 2, '4820000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '나주시', 2, '4830000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '광양시', 2, '4840000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '담양군', 2, '4850000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '구례군', 2, '4870000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '고흥군', 2, '4880000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '보성군', 2, '4890000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '화순군', 2, '4900000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '해남군', 2, '4930000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '영암군', 2, '4940000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '무안군', 2, '4950000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '함평군', 2, '4960000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '영광군', 2, '4970000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '장성군', 2, '4980000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '완도군', 2, '4990000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '진도군', 2, '5000000', id FROM region WHERE code = '6460000';
INSERT INTO region (name, level, code, parent_id) SELECT '신안군', 2, '5010000', id FROM region WHERE code = '6460000';

-- 경상북도
INSERT INTO region (name, level, code, parent_id) SELECT '포항시', 2, '5020000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '경주시', 2, '5050000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '김천시', 2, '5060000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '안동시', 2, '5070000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '구미시', 2, '5080000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '영주시', 2, '5090000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '영천시', 2, '5100000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '상주시', 2, '5110000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '문경시', 2, '5120000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '경산시', 2, '5130000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '영덕군', 2, '5180000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '청도군', 2, '5190000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '고령군', 2, '5200000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '성주군', 2, '5210000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '칠곡군', 2, '5220000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '예천군', 2, '5230000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '울진군', 2, '5250000', id FROM region WHERE code = '6470000';
INSERT INTO region (name, level, code, parent_id) SELECT '울릉군', 2, '5260000', id FROM region WHERE code = '6470000';

-- 경상남도
INSERT INTO region (name, level, code, parent_id) SELECT '진주시', 2, '5310000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '통영시', 2, '5330000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '사천시', 2, '5340000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '김해시', 2, '5350000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '밀양시', 2, '5360000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '거제시', 2, '5370000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '양산시', 2, '5380000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '의령군', 2, '5390000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '함안군', 2, '5400000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '고성군', 2, '5420000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '남해군', 2, '5430000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '하동군', 2, '5440000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '산청군', 2, '5450000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '함양군', 2, '5460000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '거창군', 2, '5470000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '합천군', 2, '5480000', id FROM region WHERE code = '6480000';
INSERT INTO region (name, level, code, parent_id) SELECT '창원시', 2, '5670000', id FROM region WHERE code = '6480000';

-- 제주특별자치도
INSERT INTO region (name, level, code, parent_id) SELECT '제주시', 2, '6510000', id FROM region WHERE code = '6500000';
INSERT INTO region (name, level, code, parent_id) SELECT '서귀포시', 2, '6520000', id FROM region WHERE code = '6500000';
