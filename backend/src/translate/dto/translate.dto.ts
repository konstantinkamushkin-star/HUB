import { IsArray, IsString, MinLength } from 'class-validator';

export class TranslateRequestDto {
  @IsString()
  @MinLength(1)
  text!: string;

  @IsString()
  sourceLanguage!: string;

  @IsString()
  targetLanguage!: string;
}

export class TranslateBatchRequestDto {
  @IsArray()
  @IsString({ each: true })
  texts!: string[];

  @IsString()
  sourceLanguage!: string;

  @IsString()
  targetLanguage!: string;
}
