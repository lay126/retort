import org.yaml.snakeyaml.Yaml as Parser
import org.yaml.snakeyaml.DumperOptions


import retort.utils.logging.Logger
import static retort.utils.Utils.delegateParameters as getParam

//Expose static function like 'yaml.load()'
//- https://stackoverflow.com/a/25603288
static def load(String yaml){
  //Parse yaml file to collection
  //- https://stackoverflow.com/a/41731617
  new Parser().load(yaml)
}

static def dump(def yaml){
  new Parser().dump(yaml)
}

/**
 * file
 * update - type : map, key: yaml path, value: value
 * index - index of separated yaml by '---'. 
 */
def update(def ret) {
  Logger logger = Logger.getLogger(this)
  def config = getParam(ret)
  
  if (!config.file) {
    logger.error('file is required.')
    createException('RC401')
  }
  
  logger.debug("FILE : ${config.file}")

  if (!config.update) {
    logger.error('file is required.')
    createException('RC402')
  } else if (!(config.update in Map)){
    logger.error('update only support Map type parameter.')
    createException('RC403')
  }
  
  // validate config.index
  def index = -1 
  if (config.index in Integer) {
    index = config.index
  } else if (config.index in CharSequence && config.index.isInteger()) {
    index = config.index as Integer
  } else if (config.index) {
    logger.error('index must be Integer of Integer String')
    createException('RC407')
  }
  
  logger.debug("UPDATE : ${config.update}")

  logger.info("Loading file : ${config.file}")
  def yamlText = readFile file: config.file
  logger.debug("""Original yaml contents
${yamlText}
""")

  def yamlSeparator = '---'
  def yamlToken
  if (index >= 0) {
    yamlToken = yamlText.split(yamlSeparator)
    logger.info("Getting ${index} out of ${yamlToken.size()}")
    yamlText = yamlToken[index].trim()
  }

  def yaml = load(yamlText)
  
  config.update.each { k, v ->
      def binding = new Binding(yaml:yaml)
      def shell = new GroovyShell(binding)
      def expression = "yaml${k} = '${v}'"
      
      shell.evaluate(expression)
  }
  
  logger.debug("${yaml}")
  
  def updatedYamlText = dumpBlock(yaml)
  
  if (index >= 0) {
    yamlToken[index] = "${updatedYamlText}\n".toString()
    if (index > 0) {
      yamlToken[index] = '\n' + yamlToken[index]
    }
    updatedYamlText = yamlToken.join(yamlSeparator)
  }
  
  logger.debug("""Updated yaml contents
${updatedYamlText}
""")

  writeFile file: config.file, text: updatedYamlText
  logger.info("Yaml update is completed.")
}

/**
 * file
 * version
 * deployName
 * dockerImage
 */
def bluegreenDeployUpdate(ret) {
  Logger logger = Logger.getLogger(this)
  def config = getParam(ret)
  
  def version
  if (config.version) {
    version = config.version
  } else if (env.VERSION) {
    logger.debug("version is not set. Using env.VERSION")
    version = env.VERSION
  } else {
    logger.error('When calling bluegreenDeployUpdate function, set the version value or set it to property VERSION.')
    createException('RC404')
  }

  
  def deployName
  if (config.deployName) {
    deployName = config.deployName
  } else if (env.APP_NAME) {
    logger.debug("deployName is not set. Using env.APP_NAME-version")
    deployName = "${env.APP_NAME}-${version}" 
  } else {
    logger.error('When calling bluegreenDeployUpdate function, set the deployName value or set it to property APP_NAME.')
    createException('RC405')
  }

  
  def dockerImage
  if (config.dockerImage) {
    dockerImage = config.dockerImage
  } else if (env.DOCKER_IMAGE) {
    logger.debug("dockerImage is not set. Using env.DOCKER_REGISTRY, env.DOCKER_IMAGE, version")
    if (env.DOCKER_REGISTRY) {
      dockerImage = "${env.DOCKER_REGISTRY}/${env.DOCKER_IMAGE}:${version}"
    } else {
      dockerImage = "${env.DOCKER_IMAGE}:${version}" 
    }
  } else {
    logger.error('When calling bluegreenDeployUpdate function, set the dockerImage value or set it to property DOCKER_IMAGE.')
    createException('RC406')
  }
  
  def data = [
    '.metadata.name': deployName,
    '.metadata.labels.version': version,
    '.spec.selector.matchLabels.version': version,
    '.spec.template.metadata.labels.version': version,
    '.spec.template.spec.containers[0].image': dockerImage
  ]
  
  logger.info("Updating ${config.file} with ${deployName}, ${version}, ${dockerImage}")
  
  def config2 = config.clone()
  config2.put('update', data) 
  update config2
}

/**
 * file
 * version
 */
def bluegreenServiceUpdate(ret) {
  Logger logger = Logger.getLogger(this)
  def config = [version: env.VERSION ]
  config = getParam(ret)
  
  def version
  if (config.version) {
    version = config.version
  } else if (env.VERSION) {
    logger.debug("version is not set. Using env.VERSION")
    version = env.VERSION
  } else {
    logger.error('When calling bluegreenServiceUpdate function, set the version value or set it to property VERSION.')
    createException('RC404')
  }

  
  def data = ['.spec.selector.version': version]
  logger.info("Updating ${config.file} with ${version}")
  def config2 = config.clone()
  config2.put('update', data) 
  update config2
}



@NonCPS
private def dumpBlock(yaml) {
  DumperOptions options = new DumperOptions()
  options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
  
  return new Parser(options).dump(yaml) 
}
